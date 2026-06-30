package lt.satsyuk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.exception.AccountNotFoundException;
import lt.satsyuk.exception.AccountOptimisticLockException;
import lt.satsyuk.mapper.AccountMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.model.RequestStatus;
import lt.satsyuk.model.RequestType;
import lt.satsyuk.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String AUTH_CLIENT_ID = "spring-app";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private RequestService requestService;

    @Mock
    private SecurityService securityService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().when(securityService.clientId()).thenReturn(AUTH_CLIENT_ID);
        accountService = new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper);
    }

    @Test
    void updateBalancePessimisticUpdatesBalanceAndReturnsMappedResponse() throws Exception {
        Client client = Client.builder().id(11L).firstName("A").lastName("B").phone("+37060000001").build();
        Account account = Account.builder()
                .id(22L)
                .client(client)
                .balance(new BigDecimal("100.00"))
                .version(0L)
                .build();
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("25.50"));
        AccountResponse response = new AccountResponse(22L, 11L, new BigDecimal("125.50"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        when(accountRepository.findByClientIdAndAuthClientIdForPessimisticUpdate(11L, AUTH_CLIENT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(accountMapper.toResponse(account)).thenReturn(response);

        AccountResponse actual = accountService.updateBalancePessimistic(request);

        assertThat(actual).isEqualTo(response);
        assertThat(account.getBalance()).isEqualByComparingTo("125.50");
        verify(requestService).completeRequest(requestId, AUTH_CLIENT_ID, objectMapper.writeValueAsString(AppResponse.ok(response)));
    }

    @Test
    void updateBalancePessimisticReturnsExistingResultForIdempotentReplay() throws Exception {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("25.50"));
        AccountResponse savedResponse = new AccountResponse(22L, 11L, new BigDecimal("125.50"));
        String savedResponseJson = objectMapper.writeValueAsString(AppResponse.ok(savedResponse));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.COMPLETED, savedResponseJson));

        AccountResponse actual = accountService.updateBalancePessimistic(request);

        assertThat(actual).isEqualTo(savedResponse);
        verify(accountRepository, never()).findByClientIdAndAuthClientIdForPessimisticUpdate(any(), any());
    }

    @Test
    void updateBalancePessimisticThrowsWhenAccountNotFound() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, BigDecimal.ONE);
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        when(accountRepository.findByClientIdAndAuthClientIdForPessimisticUpdate(11L, AUTH_CLIENT_ID)).thenReturn(Optional.empty());

        ThrowingCallable action = () -> accountService.updateBalancePessimistic(request);

        assertThatThrownBy(action)
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("client id=11");
        verify(requestService).failRequest(eq(requestId), eq(AUTH_CLIENT_ID), any(String.class));
    }

    @Test
    void getByClientIdReturnsMappedResponse() {
        Client client = Client.builder().id(11L).firstName("A").lastName("B").phone("+37060000001").build();
        Account account = Account.builder()
                .id(22L)
                .client(client)
                .balance(new BigDecimal("10.00"))
                .version(1L)
                .build();
        AccountResponse response = new AccountResponse(22L, 11L, new BigDecimal("10.00"));

        when(accountRepository.findByClientIdAndAuthClientId(11L, AUTH_CLIENT_ID)).thenReturn(Optional.of(account));
        when(accountMapper.toResponse(account)).thenReturn(response);

        assertThat(accountService.getByClientId(11L, AUTH_CLIENT_ID)).isEqualTo(response);
    }

    @Test
    void safeUpdateRetriesOnOptimisticConflictAndEventuallySucceeds() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        BigDecimal amount = new BigDecimal("5.00");
        AccountResponse expected = new AccountResponse(22L, 11L, new BigDecimal("15.00"));

        doThrow(new ObjectOptimisticLockingFailureException(Account.class, 11L))
                .doThrow(new OptimisticLockException())
                .doReturn(expected)
                .when(spyService).updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);

        AccountResponse actual = spyService.safeUpdate(11L, amount, AUTH_CLIENT_ID);

        assertThat(actual).isEqualTo(expected);
        verify(spyService, times(3)).updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);
    }

    @Test
    void safeUpdateThrowsAfterMaxRetries() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        BigDecimal amount = new BigDecimal("5.00");

        doThrow(new ObjectOptimisticLockingFailureException(Account.class, 11L))
                .when(spyService).updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);

        assertThatThrownBy(() -> spyService.safeUpdate(11L, amount, AUTH_CLIENT_ID))
                .isInstanceOf(AccountOptimisticLockException.class)
                .hasMessageContaining("client id=11");
        verify(spyService, times(3)).updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);
    }

    @Test
    void safeUpdateRethrowsNonOptimisticException() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        BigDecimal amount = new BigDecimal("5.00");

        doThrow(new IllegalStateException("boom"))
                .when(spyService).updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);

        assertThatThrownBy(() -> spyService.safeUpdate(11L, amount, AUTH_CLIENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void updateBalanceOptimisticDelegatesToSafeUpdate() throws Exception {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        AccountResponse expected = new AccountResponse(22L, 11L, new BigDecimal("13.00"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        doReturn(expected).when(spyService).safeUpdate(11L, new BigDecimal("3.00"), AUTH_CLIENT_ID);

        assertThat(spyService.updateBalanceOptimistic(request)).isEqualTo(expected);
        verify(requestService).completeRequest(requestId, AUTH_CLIENT_ID, objectMapper.writeValueAsString(AppResponse.ok(expected)));
    }

    @Test
    void updateBalanceOptimisticTxThrowsWhenAccountMissing() {
        when(accountRepository.findByClientIdAndAuthClientId(11L, AUTH_CLIENT_ID)).thenReturn(Optional.empty());
        BigDecimal amount = new BigDecimal("1.00");
        ThrowingCallable action = () -> accountService.updateBalanceOptimisticTx(11L, amount, AUTH_CLIENT_ID);

        assertThatThrownBy(action)
                .isInstanceOf(AccountNotFoundException.class);
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void updateBalanceOptimisticReturnsExistingResultForIdempotentReplay() throws Exception {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        AccountResponse savedResponse = new AccountResponse(22L, 11L, new BigDecimal("13.00"));
        String savedResponseJson = objectMapper.writeValueAsString(AppResponse.ok(savedResponse));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.COMPLETED, savedResponseJson));

        AccountResponse actual = accountService.updateBalanceOptimistic(request);

        assertThat(actual).isEqualTo(savedResponse);
    }

    @Test
    void updateBalanceOptimisticFailsRequestOnAccountNotFound() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        doThrow(new AccountNotFoundException(11L)).when(spyService).safeUpdate(11L, new BigDecimal("3.00"), AUTH_CLIENT_ID);

        assertThatThrownBy(() -> spyService.updateBalanceOptimistic(request))
                .isInstanceOf(AccountNotFoundException.class);
        verify(requestService).failRequest(eq(requestId), eq(AUTH_CLIENT_ID), any(String.class));
    }

    @Test
    void updateBalanceOptimisticFailsRequestOnOptimisticLock() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        doThrow(new AccountOptimisticLockException(11L)).when(spyService).safeUpdate(11L, new BigDecimal("3.00"), AUTH_CLIENT_ID);

        assertThatThrownBy(() -> spyService.updateBalanceOptimistic(request))
                .isInstanceOf(AccountOptimisticLockException.class);
        verify(requestService).failRequest(eq(requestId), eq(AUTH_CLIENT_ID), any(String.class));
    }

    @Test
    void updateBalanceOptimisticFailsRequestOnUnexpectedException() {
        AccountService spyService = spy(new AccountService(accountRepository, accountMapper, transactionManager,
                requestService, securityService, objectMapper));
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        doThrow(new IllegalStateException("boom")).when(spyService).safeUpdate(11L, new BigDecimal("3.00"), AUTH_CLIENT_ID);

        assertThatThrownBy(() -> spyService.updateBalanceOptimistic(request))
                .isInstanceOf(IllegalStateException.class);
        verify(requestService).failRequest(eq(requestId), eq(AUTH_CLIENT_ID), any(String.class));
    }

    @Test
    void updateBalancePessimisticFailsRequestOnUnexpectedException() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, BigDecimal.ONE);
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, false, RequestStatus.PENDING, null));
        when(accountRepository.findByClientIdAndAuthClientIdForPessimisticUpdate(11L, AUTH_CLIENT_ID))
                .thenThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> accountService.updateBalancePessimistic(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db error");
        verify(requestService).failRequest(eq(requestId), eq(AUTH_CLIENT_ID), any(String.class));
    }

    @Test
    void readSavedResponseReturnsNullForBlankData() {
        AccountResponse result = accountService.readSavedResponse(null);
        assertThat(result).isNull();

        result = accountService.readSavedResponse("  ");
        assertThat(result).isNull();
    }

    @Test
    void readSavedResponseThrowsForMalformedJson() {
        assertThatThrownBy(() -> accountService.readSavedResponse("{invalid json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize saved response");
    }

    @Test
    void updateBalancePessimisticThrowsOnFailedIdempotentReplay() throws Exception {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("25.50"));
        UUID requestId = UUID.randomUUID();
        String errorJson = objectMapper.writeValueAsString(
                AppResponse.error(AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "something went wrong"));

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.FAILED, errorJson));

        assertThatThrownBy(() -> accountService.updateBalancePessimistic(request))
                .isInstanceOf(lt.satsyuk.exception.AccountUpdateFailedException.class)
                .hasMessageContaining("something went wrong");
    }

    @Test
    void updateBalancePessimisticThrowsOnInProgressIdempotentReplay() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("25.50"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_PESSIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.PROCESSING, null));

        assertThatThrownBy(() -> accountService.updateBalancePessimistic(request))
                .isInstanceOf(lt.satsyuk.exception.AccountUpdateInProgressException.class)
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void updateBalanceOptimisticThrowsOnFailedIdempotentReplay() throws Exception {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        UUID requestId = UUID.randomUUID();
        String errorJson = objectMapper.writeValueAsString(
                AppResponse.error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), "optimistic lock failed"));

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.FAILED, errorJson));

        assertThatThrownBy(() -> accountService.updateBalanceOptimistic(request))
                .isInstanceOf(lt.satsyuk.exception.AccountUpdateFailedException.class)
                .hasMessageContaining("optimistic lock failed");
    }

    @Test
    void updateBalanceOptimisticThrowsOnInProgressIdempotentReplay() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(null, 11L, new BigDecimal("3.00"));
        UUID requestId = UUID.randomUUID();

        when(requestService.createPendingRequestIfAbsent(null, request, RequestType.UPDATE_BALANCE_OPTIMISTIC, AUTH_CLIENT_ID))
                .thenReturn(new RequestService.CreateRequestResult(requestId, true, RequestStatus.PENDING, null));

        assertThatThrownBy(() -> accountService.updateBalanceOptimistic(request))
                .isInstanceOf(lt.satsyuk.exception.AccountUpdateInProgressException.class)
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void parseErrorResponseReturnsNullForBlank() {
        assertThat(accountService.parseErrorResponse(null)).isNull();
        assertThat(accountService.parseErrorResponse("  ")).isNull();
    }

    @Test
    void parseErrorResponseReturnsNullForMalformedJson() {
        assertThat(accountService.parseErrorResponse("{bad json")).isNull();
    }
}
