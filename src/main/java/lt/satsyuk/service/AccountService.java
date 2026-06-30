package lt.satsyuk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.exception.AccountNotFoundException;
import lt.satsyuk.exception.AccountOptimisticLockException;
import lt.satsyuk.mapper.AccountMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.RequestType;
import lt.satsyuk.repository.AccountRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

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
            return readSavedResponse(result.savedResponseData());
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
        } catch (AccountNotFoundException | AccountOptimisticLockException ex) {
            requestService.failRequest(requestId, authClientId, writeJson(
                    AppResponse.error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), ex.getMessage())
            ));
            throw ex;
        } catch (RuntimeException ex) {
            requestService.failRequest(requestId, authClientId, writeJson(
                    AppResponse.error(AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage())
            ));
            throw ex;
        }
    }

    public AccountResponse updateBalanceOptimistic(UpdateBalanceRequest request) {
        String authClientId = securityService.clientId();
        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                request.idempotencyKey(), request, RequestType.UPDATE_BALANCE_OPTIMISTIC, authClientId);

        if (result.alreadyExisted()) {
            return readSavedResponse(result.savedResponseData());
        }

        UUID requestId = result.requestId();
        try {
            AccountResponse response = safeUpdate(request.clientId(), request.amount(), authClientId);
            requestService.completeRequest(requestId, authClientId, writeJson(AppResponse.ok(response)));
            return response;
        } catch (AccountNotFoundException | AccountOptimisticLockException ex) {
            requestService.failRequest(requestId, authClientId, writeJson(
                    AppResponse.error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), ex.getMessage())
            ));
            throw ex;
        } catch (RuntimeException ex) {
            requestService.failRequest(requestId, authClientId, writeJson(
                    AppResponse.error(AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ex.getMessage())
            ));
            throw ex;
        }
    }

    public AccountResponse getByClientId(Long clientId, String authClientId) {
        Account account = accountRepository.findByClientIdAndAuthClientId(clientId, authClientId)
                .orElseThrow(() -> new AccountNotFoundException(clientId));
        return accountMapper.toResponse(account);
    }

    public AccountResponse safeUpdate(Long clientId, BigDecimal amount, String authClientId) {
        for (int i = 0; i < MAX_OPTIMISTIC_RETRIES; i++) {
            try {
                return Objects.requireNonNull(
                        transactionTemplate.execute(status -> updateBalanceOptimisticTx(clientId, amount, authClientId)),
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
        if (responseData == null || responseData.isBlank()) {
            return null;
        }
        try {
            AppResponse<AccountResponse> appResponse = objectMapper.readValue(responseData,
                    objectMapper.getTypeFactory().constructParametricType(AppResponse.class, AccountResponse.class));
            return appResponse.data();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize saved response", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize response", ex);
        }
    }
}
