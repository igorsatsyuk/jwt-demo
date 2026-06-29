package lt.satsyuk.controller;

import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.service.AccountService;
import lt.satsyuk.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private static final String AUTH_CLIENT_ID = "spring-app";

    @Mock
    private AccountService accountService;

    @Mock
    private SecurityService securityService;

    private AccountController accountController;

    @BeforeEach
    void setUp() {
        when(securityService.clientId()).thenReturn(AUTH_CLIENT_ID);
        accountController = new AccountController(accountService, securityService);
    }

    @Test
    void updateBalancePessimisticReturnsServiceResult() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(1L, new BigDecimal("10.00"));
        AccountResponse response = new AccountResponse(2L, 1L, new BigDecimal("110.00"));
        when(accountService.updateBalancePessimistic(request, AUTH_CLIENT_ID)).thenReturn(response);

        var apiResponse = accountController.updateBalancePessimistic(request);

        assertThat(apiResponse.code()).isZero();
        assertThat(apiResponse.data()).isEqualTo(response);
        verify(accountService).updateBalancePessimistic(request, AUTH_CLIENT_ID);
    }

    @Test
    void updateBalanceOptimisticReturnsServiceResult() {
        UpdateBalanceRequest request = new UpdateBalanceRequest(1L, new BigDecimal("10.00"));
        AccountResponse response = new AccountResponse(2L, 1L, new BigDecimal("110.00"));
        when(accountService.updateBalanceOptimistic(request, AUTH_CLIENT_ID)).thenReturn(response);

        var apiResponse = accountController.updateBalanceOptimistic(request);

        assertThat(apiResponse.code()).isZero();
        assertThat(apiResponse.data()).isEqualTo(response);
        verify(accountService).updateBalanceOptimistic(request, AUTH_CLIENT_ID);
    }

    @Test
    void getByClientIdReturnsServiceResult() {
        AccountResponse response = new AccountResponse(2L, 1L, new BigDecimal("110.00"));
        when(accountService.getByClientId(1L, AUTH_CLIENT_ID)).thenReturn(response);

        var apiResponse = accountController.getByClientId(1L);

        assertThat(apiResponse.code()).isZero();
        assertThat(apiResponse.data()).isEqualTo(response);
        verify(accountService).getByClientId(1L, AUTH_CLIENT_ID);
    }
}
