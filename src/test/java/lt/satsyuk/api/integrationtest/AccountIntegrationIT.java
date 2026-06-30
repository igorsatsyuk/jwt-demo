package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.api.util.KeycloakIntegrationTest;
import lt.satsyuk.dto.AccountResponse;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.UpdateBalanceRequest;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.model.ClientAccess;
import lt.satsyuk.repository.AccountRepository;
import lt.satsyuk.repository.ClientAccessRepository;
import lt.satsyuk.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.security.RateLimitingFilter;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AccountIntegrationIT extends KeycloakIntegrationTest {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final ClientAccessRepository clientAccessRepository;

    AccountIntegrationIT(@Qualifier("keycloakProperties") KeycloakProperties props,
                         CacheManager cacheManager,
                         RateLimitingFilter rateLimitingFilter,
                         AccountRepository accountRepository,
                         ClientRepository clientRepository,
                         ClientAccessRepository clientAccessRepository) {
        super(props, cacheManager, rateLimitingFilter);
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.clientAccessRepository = clientAccessRepository;
    }

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        clientAccessRepository.deleteAll();
        clientRepository.deleteAll();
    }

    @Test
    void update_balance_pessimistic_success() {
        Account account = saveAccount("100.00", "+37061111111");
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        AccountResponse response = postAndReturnData(
                accountUrl + "/balance/pessimistic",
                token,
                new UpdateBalanceRequest(null, account.getClient().getId(), new BigDecimal("250.75")),
                AccountResponse.class
        );

        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.clientId()).isEqualTo(account.getClient().getId());
        assertThat(response.balance()).isEqualByComparingTo("350.75");

        Account persisted = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("350.75");
    }

    @Test
    void update_balance_optimistic_success() {
        Account account = saveAccount("50.00", "+37062222222");
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        AccountResponse response = postAndReturnData(
                accountUrl + "/balance/optimistic",
                token,
                new UpdateBalanceRequest(null, account.getClient().getId(), new BigDecimal("75.00")),
                AccountResponse.class
        );

        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.clientId()).isEqualTo(account.getClient().getId());
        assertThat(response.balance()).isEqualByComparingTo("125.00");
    }

    @Test
    void get_account_by_client_id_success() {
        Account account = saveAccount("10.00", "+37063333333");
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        AccountResponse response = getAndReturnData(
                accountUrl + "/client/" + account.getClient().getId(),
                token,
                AccountResponse.class
        );

        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.clientId()).isEqualTo(account.getClient().getId());
        assertThat(response.balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void update_balance_pessimistic_not_found_returns_404() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> response = requestPost(
                accountUrl + "/balance/pessimistic",
                token,
                null,
                new UpdateBalanceRequest(null, 999999L, new BigDecimal("100.00"))
        );

        assertErrorStatusAndBody(
                response,
                HttpStatus.NOT_FOUND,
                AppResponse.ErrorCode.NOT_FOUND.getCode(),
                "Account for client id=999999 not found"
        );
    }

    @Test
    void get_account_not_accessible_by_other_auth_client() {
        Client client = clientRepository.save(
                Client.builder()
                        .firstName("Other")
                        .lastName("Client")
                        .phone("+37069999999")
                        .build()
        );
        clientAccessRepository.save(ClientAccess.builder()
                .clientId(client.getId())
                .authClientId("other-client")
                .build());

        Account account = accountRepository.saveAndFlush(
                Account.builder()
                        .balance(new java.math.BigDecimal("10.00"))
                        .client(client)
                        .build()
        );

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> response = requestGet(
                accountUrl + "/client/" + account.getClient().getId(),
                token
        );

        assertErrorStatusAndBody(
                response,
                HttpStatus.NOT_FOUND,
                AppResponse.ErrorCode.NOT_FOUND.getCode(),
                "Account for client id=" + account.getClient().getId() + " not found"
        );
    }

    private Account saveAccount(String balance, String phone) {
        Client client = clientRepository.save(
                Client.builder()
                        .firstName("Test")
                        .lastName("User")
                        .phone(phone)
                        .build()
        );

        clientAccessRepository.save(ClientAccess.builder()
                .clientId(client.getId())
                .authClientId("spring-app")
                .build());

        return accountRepository.saveAndFlush(
                Account.builder()
                        .balance(new BigDecimal(balance))
                        .client(client)
                        .build()
        );
    }
}

