package lt.satsyuk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.exception.AccountNotFoundException;
import lt.satsyuk.exception.AccountOptimisticLockException;
import lt.satsyuk.exception.AccountUpdateInProgressException;
import lt.satsyuk.mapper.AccountMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.RequestType;
import lt.satsyuk.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class AccountService {
    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final TransactionTemplate transactionTemplate;
    private final RequestService requestService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository,
                          AccountMapper accountMapper,
                          PlatformTransactionManager transactionManager,
                          RequestService requestService,
                          SecurityService securityService,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requestService = requestService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountResponse updateBalancePessimistic(UpdateBalanceRequest request) {
        String authClientId = securityService.clientId();
        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                request.idempotencyKey(), request, RequestType.UPDATE_BALANCE_PESSIMISTIC, authClientId);

        if (result.alreadyExisted()) {
            return handleExistingRequest(result);
        }

        UUID requestId = result.requestId();
        try {
            Account account = accountRepository.findByClientIdAndAuthClientIdForPessimisticUpdate(request.clientId(), authClientId)
                    .orElseThrow(() -> new AccountNotFoundException(request.clientId()));
            account.updateBalance(account.getBalance().add(request.amount()));
            Account saved = accountRepository.saveAndFlush(account);
            AccountResponse response = accountMapper.toResponse(saved);
            requestService.completeRequest(requestId, authClientId, writeJson(AppResponse.ok(response)));
            return response;
        } catch (AccountNotFoundException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        } catch (AccountOptimisticLockException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        } catch (RuntimeException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        }
    }

    public AccountResponse updateBalanceOptimistic(UpdateBalanceRequest request) {
        String authClientId = securityService.clientId();
        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                request.idempotencyKey(), request, RequestType.UPDATE_BALANCE_OPTIMISTIC, authClientId);

        if (result.alreadyExisted()) {
            return handleExistingRequest(result);
        }

        UUID requestId = result.requestId();
        try {
            AccountResponse response = safeUpdate(request.clientId(), request.amount(), authClientId,
                    requestId, authClientId);
            return response;
        } catch (AccountNotFoundException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        } catch (AccountOptimisticLockException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        } catch (RuntimeException ex) {
            markRequestFailed(requestId, authClientId, ex);
            throw ex;
        }
    }

    public AccountResponse getByClientId(Long clientId, String authClientId) {
        Account account = accountRepository.findByClientIdAndAuthClientId(clientId, authClientId)
                .orElseThrow(() -> new AccountNotFoundException(clientId));
        return accountMapper.toResponse(account);
    }

    public AccountResponse safeUpdate(Long clientId, BigDecimal amount, String authClientId,
                                      UUID requestId, String requestAuthClientId) {
        for (int i = 0; i < MAX_OPTIMISTIC_RETRIES; i++) {
            try {
                return Objects.requireNonNull(
                        transactionTemplate.execute(status -> {
                            AccountResponse response = updateBalanceOptimisticTx(clientId, amount, authClientId);
                            requestService.completeRequest(requestId, requestAuthClientId,
                                    writeJson(AppResponse.ok(response)));
                            return response;
                        }),
                        "Transaction returned null result"
                );
            } catch (RuntimeException ex) {
                if (!isOptimisticConflict(ex)) {
                    throw ex;
                }
                if (i == MAX_OPTIMISTIC_RETRIES - 1) {
                    throw new AccountOptimisticLockException(clientId);
                }
            }
        }
        throw new AccountOptimisticLockException(clientId);
    }

    protected AccountResponse updateBalanceOptimisticTx(Long clientId, BigDecimal amount, String authClientId) {
        Account account = accountRepository.findByClientIdAndAuthClientId(clientId, authClientId)
                .orElseThrow(() -> new AccountNotFoundException(clientId));
        account.updateBalance(account.getBalance().add(amount));
        Account saved = accountRepository.saveAndFlush(account);
        return accountMapper.toResponse(saved);
    }

    private boolean isOptimisticConflict(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    AccountResponse readSavedResponse(String responseData) {
        AppResponse<AccountResponse> parsed = parseErrorResponse(responseData);
        return parsed != null ? parsed.data() : null;
    }

    private AccountResponse handleExistingRequest(RequestService.CreateRequestResult result) {
        return switch (result.status()) {
            case COMPLETED -> readSavedResponse(result.savedResponseData());
            case FAILED -> rethrowStoredException(result.savedResponseData());
            case PENDING, PROCESSING -> throw new AccountUpdateInProgressException(result.requestId());
        };
    }

    private AccountResponse rethrowStoredException(String responseData) {
        StoredError stored = parseStoredError(responseData);
        if (stored == null) {
            throw new IllegalStateException("Request failed with unknown error");
        }
        throw switch (stored.exceptionType()) {
            case "AccountNotFoundException" -> new AccountNotFoundException(stored.clientId());
            case "AccountOptimisticLockException" -> new AccountOptimisticLockException(stored.clientId());
            default -> new IllegalStateException(stored.message());
        };
    }

    StoredError parseStoredError(String responseData) {
        if (responseData == null || responseData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(responseData, StoredError.class);
        } catch (JsonProcessingException _) {
            return null;
        }
    }

    AppResponse<AccountResponse> parseErrorResponse(String responseData) {
        if (responseData == null || responseData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(responseData,
                    objectMapper.getTypeFactory().constructParametricType(AppResponse.class, AccountResponse.class));
        } catch (JsonProcessingException _) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize response", ex);
        }
    }

    private void markRequestFailed(UUID requestId, String authClientId, RuntimeException ex) {
        StoredError stored = switch (ex) {
            case AccountNotFoundException e -> new StoredError(
                    AppResponse.ErrorCode.NOT_FOUND.getCode(), e.getMessage(),
                    e.getClass().getSimpleName(), e.getClientId());
            case AccountOptimisticLockException e -> new StoredError(
                    AppResponse.ErrorCode.CONFLICT.getCode(), e.getMessage(),
                    e.getClass().getSimpleName(), e.getClientId());
            default -> new StoredError(
                    AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "Internal server error",
                    ex.getClass().getSimpleName(), null);
        };
        try {
            requestService.failRequest(requestId, authClientId, writeJson(stored));
        } catch (RuntimeException ex2) {
            log.warn("Failed to mark request {} as FAILED: {}", requestId, ex2.getMessage());
        }
    }

    public record StoredError(int code, String message, String exceptionType, Long clientId) {}
}
