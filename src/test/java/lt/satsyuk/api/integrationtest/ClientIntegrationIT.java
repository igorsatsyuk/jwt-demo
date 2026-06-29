package lt.satsyuk.api.integrationtest;

import com.fasterxml.jackson.core.type.TypeReference;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.api.util.KeycloakIntegrationTest;
import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.dto.KeycloakTokenResponse;
import lt.satsyuk.dto.RequestAcceptedResponse;
import lt.satsyuk.dto.RequestStatusResponse;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.model.ClientAccess;
import lt.satsyuk.model.RequestStatus;
import lt.satsyuk.repository.AccountRepository;
import lt.satsyuk.repository.ClientAccessRepository;
import lt.satsyuk.repository.ClientRepository;
import lt.satsyuk.repository.RequestRepository;
import lt.satsyuk.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.security.RateLimitingFilter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = lt.satsyuk.MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ClientIntegrationIT extends KeycloakIntegrationTest {

    public static final String JOHN = "John";
    public static final String DOE = "Doe";
    public static final String ALICE = "Alice";
    public static final String SMITH = "Smith";
    public static final String PHONE = "+37061234567";
    private final ClientRepository repo;
    private final AccountRepository accountRepository;
    private final RequestRepository requestRepository;
    private final ClientAccessRepository clientAccessRepository;

    ClientIntegrationIT(@Qualifier("keycloakProperties") KeycloakProperties props,
                        CacheManager cacheManager,
                        RateLimitingFilter rateLimitingFilter,
                        ClientRepository repo,
                        AccountRepository accountRepository,
                        RequestRepository requestRepository,
                        ClientAccessRepository clientAccessRepository) {
        super(props, cacheManager, rateLimitingFilter);
        this.repo = repo;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.clientAccessRepository = clientAccessRepository;
    }

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        clientAccessRepository.deleteAll();
        repo.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    void create_client_success_and_persistence() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        CreateClientRequest req = new CreateClientRequest(null, JOHN, DOE, PHONE);

        RequestAcceptedResponse accepted = postAndReturnData(clientUrl, token, req, HttpStatus.ACCEPTED, RequestAcceptedResponse.class);

        assertThat(accepted.requestId()).isNotNull();
        assertThat(accepted.status()).isEqualTo(RequestStatus.PENDING);

        RequestStatusResponse statusResponse = awaitRequestStatus(token, accepted.requestId(), RequestStatus.COMPLETED);
        AppResponse<ClientResponse> finalResponse = readNestedResponse(statusResponse);
        ClientResponse data = objectMapper.convertValue(finalResponse.data(), ClientResponse.class);

        assertThat(data.phone()).isEqualTo(req.phone());
        assertThat(data.id()).isNotNull();
        assertThat(finalResponse.code()).isZero();

        assertThat(repo.existsByPhone(req.phone())).isTrue();
        assertThat(requestRepository.findByIdAndAuthClientId(accepted.requestId(), "spring-app")).isPresent();
        assertThat(clientAccessRepository.existsByClientIdAndAuthClientId(data.id(), "spring-app")).isTrue();
        Account createdAccount = accountRepository.findByClientId(data.id()).orElse(null);
        assertThat(createdAccount).isNotNull();
        assertThat(createdAccount.getBalance()).isEqualByComparingTo("0");

        ClientResponse fetched = getAndReturnData(clientUrl + "/" + data.id(), token, ClientResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(data.id());
        assertThat(fetched.phone()).isEqualTo(data.phone());
    }

    @Test
    void get_client_not_accessible_by_other_auth_client() {
        Client saved = repo.save(Client.builder().firstName(ALICE).lastName(SMITH).phone("+37060000000").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(saved.getId()).authClientId("other-client").build());

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/" + saved.getId(), token);

        assertErrorStatusAndBody(resp, HttpStatus.NOT_FOUND,
                AppResponse.ErrorCode.NOT_FOUND.getCode(),
                "Client with id=" + saved.getId() + " not found");
    }

    @Test
    void search_clients_only_returns_accessible() {
        Client alice = repo.save(Client.builder().firstName("Alice").lastName("Brown").phone("+37070000001").build());
        Client mike = repo.save(Client.builder().firstName("Mike").lastName("Alister").phone("+37070000002").build());
        Client alix = repo.save(Client.builder().firstName("Alix").lastName("Stone").phone("+37070000004").build());
        Client john = repo.save(Client.builder().firstName("John").lastName("Doe").phone("+37070000003").build());

        clientAccessRepository.save(ClientAccess.builder().clientId(alice.getId()).authClientId("spring-app").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(mike.getId()).authClientId("spring-app").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(alix.getId()).authClientId("other-client").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(john.getId()).authClientId("other-client").build());

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/search?q=ali", token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isZero();

        var clients = objectMapper.convertValue(resp.getBody().data(), new TypeReference<java.util.List<ClientResponse>>() {});
        assertThat(clients).hasSize(2);
        assertThat(clients)
                .extracting(ClientResponse::phone)
                .containsExactly("+37070000001", "+37070000002");
    }

    @Test
    void create_client_existing_phone_adds_access_and_returns_existing() {
        Client existing = Client.builder().firstName("Jane").lastName("Roe").phone(PHONE).build();
        repo.save(existing);
        clientAccessRepository.save(ClientAccess.builder().clientId(existing.getId()).authClientId("spring-app").build());

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);
        CreateClientRequest req = new CreateClientRequest(null, JOHN, DOE, PHONE);

        RequestAcceptedResponse accepted = postAndReturnData(clientUrl, token, req, HttpStatus.ACCEPTED, RequestAcceptedResponse.class);
        RequestStatusResponse statusResponse = awaitRequestStatus(token, accepted.requestId(), RequestStatus.COMPLETED);
        AppResponse<ClientResponse> finalResponse = readNestedResponse(statusResponse);
        ClientResponse data = objectMapper.convertValue(finalResponse.data(), ClientResponse.class);

        assertThat(data.id()).isEqualTo(existing.getId());
        assertThat(data.phone()).isEqualTo(PHONE);
        assertThat(finalResponse.code()).isZero();
    }

    @Test
    void get_client_success() {
        Client saved = repo.save(Client.builder().firstName(ALICE).lastName(SMITH).phone("+37060000000").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(saved.getId()).authClientId("spring-app").build());

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ClientResponse data = getAndReturnData(clientUrl + "/" + saved.getId(), token, ClientResponse.class);

        assertThat(data.id()).isEqualTo(saved.getId());
        assertThat(data.phone()).isEqualTo(saved.getPhone());
    }

    @Test
    void search_clients_success() {
        Client c1 = repo.save(Client.builder().firstName("Alice").lastName("Brown").phone("+37070000001").build());
        Client c2 = repo.save(Client.builder().firstName("Mike").lastName("Alister").phone("+37070000002").build());
        Client c3 = repo.save(Client.builder().firstName("Alix").lastName("Stone").phone("+37070000004").build());
        Client c4 = repo.save(Client.builder().firstName("John").lastName("Doe").phone("+37070000003").build());

        clientAccessRepository.save(ClientAccess.builder().clientId(c1.getId()).authClientId("spring-app").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(c2.getId()).authClientId("spring-app").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(c3.getId()).authClientId("spring-app").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(c4.getId()).authClientId("spring-app").build());

        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/search?q=ali", token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isZero();

        var clients = objectMapper.convertValue(resp.getBody().data(), new TypeReference<java.util.List<ClientResponse>>() {});
        assertThat(clients).hasSize(2);
        assertThat(clients)
                .extracting(ClientResponse::phone)
                .containsExactly("+37070000001", "+37070000002");
    }

    @Test
    void search_clients_query_too_short_returns_400() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/search?q=ab", token);

        assertErrorStatusAndBody(resp, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.BAD_REQUEST.getCode(),
                "Search query must contain at least " + ClientService.MIN_SEARCH_QUERY_LENGTH + " characters");
    }

    @Test
    void search_clients_forbidden_when_user_has_no_role() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/search?q=alice", token);

        assertErrorStatusAndBody(resp, HttpStatus.FORBIDDEN,
                AppResponse.ErrorCode.FORBIDDEN.getCode(),
                AppResponse.ErrorCode.FORBIDDEN.getDescription());
    }

    @Test
    void get_client_not_found_returns_404() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/999999", token);

        assertErrorStatusAndBody(resp, HttpStatus.NOT_FOUND,
                AppResponse.ErrorCode.NOT_FOUND.getCode(),
                "Client with id=999999 not found");
    }

    @Test
    void get_client_unauthorized() {
        Client saved = repo.save(Client.builder().firstName("Bob").lastName("Brown").phone("+37063333333").build());

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/" + saved.getId());

        assertErrorStatusAndBody(resp, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                AppResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void get_client_unauthorized_after_logout() {
        Client saved = repo.save(Client.builder().firstName(ALICE).lastName(SMITH).phone("+37060000000").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(saved.getId()).authClientId("spring-app").build());

        KeycloakTokenResponse tokens = loginAndGetData(USERNAME, USER_PASSWORD);
        String accessToken = tokens.getAccessToken();

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/" + saved.getId(), accessToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AppResponse<Void>> logoutResponse = logoutRequest(tokens.getRefreshToken());
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AppResponse<Object>> errorResponse = requestGet(clientUrl + "/" + saved.getId(), accessToken);
        assertErrorStatusAndBody(errorResponse, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                AppResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void get_client_forbidden_when_user_has_no_role() {
        Client saved = repo.save(Client.builder().firstName("Carol").lastName("White").phone("+37064444444").build());
        clientAccessRepository.save(ClientAccess.builder().clientId(saved.getId()).authClientId("spring-app").build());

        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/" + saved.getId(), token);

        assertErrorStatusAndBody(resp, HttpStatus.FORBIDDEN,
                AppResponse.ErrorCode.FORBIDDEN.getCode(),
                AppResponse.ErrorCode.FORBIDDEN.getDescription());
    }

    @Test
    void get_client_invalid_id_returns_bad_request() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(clientUrl + "/invalid-id", token);

        assertErrorStatusAndBody(resp, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.BAD_REQUEST.getCode(),
                "Invalid value: invalid-id");
    }

    @Test
    void create_client_unauthorized() {
        CreateClientRequest req = new CreateClientRequest(null, "No", "Token", "+37061111111");

        ResponseEntity<AppResponse<Object>> resp = requestPost(clientUrl, null, req);

        assertErrorStatusAndBody(resp, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                AppResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void create_client_forbidden_when_user_has_no_role() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);
        CreateClientRequest req = new CreateClientRequest(null, "No", "Role", "+37062222222");

        ResponseEntity<AppResponse<Object>> resp = requestPost(clientUrl, token, null, req);

        assertErrorStatusAndBody(resp, HttpStatus.FORBIDDEN,
                AppResponse.ErrorCode.FORBIDDEN.getCode(),
                AppResponse.ErrorCode.FORBIDDEN.getDescription());
    }

    @Test
    void create_client_validation_error() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);
        CreateClientRequest req = new CreateClientRequest(null, "", DOE, "abc");

        ResponseEntity<AppResponse<Object>> response = requestPost(clientUrl, token, null, req);

        Set<String> expected = Set.of(
                "phone: phone must be valid",
                "firstName: firstName is required"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.BAD_REQUEST.getCode(),
                expected);
    }

    @Test
    void create_client_validation_error_russian_locale() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);
        CreateClientRequest req = new CreateClientRequest(null, "", DOE, "abc");

        ResponseEntity<AppResponse<Object>> response = requestPost(clientUrl, token, "ru", req);

        Set<String> expected = Set.of(
                "phone: Неверный формат телефона",
                "firstName: Имя обязательно"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.BAD_REQUEST.getCode(),
                expected);
    }

    @Test
    void get_request_status_not_found_returns_404() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(requestUrl + "/" + UUID.randomUUID(), token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo(AppResponse.ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void get_request_status_unauthorized() {
        ResponseEntity<AppResponse<Object>> resp = requestGet(requestUrl + "/" + UUID.randomUUID());

        assertErrorStatusAndBody(resp, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                AppResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void get_request_status_forbidden_when_user_has_no_role() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(requestUrl + "/" + UUID.randomUUID(), token);

        assertErrorStatusAndBody(resp, HttpStatus.FORBIDDEN,
                AppResponse.ErrorCode.FORBIDDEN.getCode(),
                AppResponse.ErrorCode.FORBIDDEN.getDescription());
    }

    @Test
    void get_request_status_invalid_id_returns_bad_request() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<AppResponse<Object>> resp = requestGet(requestUrl + "/invalid-id", token);

        assertErrorStatusAndBody(resp, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.BAD_REQUEST.getCode(),
                "Invalid value: invalid-id");
    }

    private RequestStatusResponse awaitRequestStatus(String token, UUID requestId, RequestStatus expectedStatus) {
        final RequestStatusResponse[] holder = new RequestStatusResponse[1];

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    RequestStatusResponse statusResponse = getAndReturnData(requestUrl + "/" + requestId, token, RequestStatusResponse.class);
                    assertThat(statusResponse.status()).isEqualTo(expectedStatus);
                    if (expectedStatus == RequestStatus.COMPLETED || expectedStatus == RequestStatus.FAILED) {
                        assertThat(statusResponse.response()).isNotNull();
                    }
                    holder[0] = statusResponse;
                });

        return holder[0];
    }

    private <T> AppResponse<T> readNestedResponse(RequestStatusResponse statusResponse) {
        return objectMapper.convertValue(statusResponse.response(), new TypeReference<>() {});
    }
}

