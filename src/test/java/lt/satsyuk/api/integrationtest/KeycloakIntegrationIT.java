package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.auth.KeycloakProperties;
import lt.satsyuk.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class KeycloakIntegrationIT {

    @Autowired
    private KeycloakProperties props;

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    private String loginUrl;
    private String refreshUrl;
    private String logoutUrl;

    private String userUrl;
    private String adminUrl;

    private static final String USERNAME = "user";
    private static final String USER_PASSWORD = "password";

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @BeforeEach
    void setUp() {
        String baseUrl = "http://localhost:" + port;

        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl(baseUrl)
                .build();

        String mainUrl = baseUrl + "/api";
        loginUrl = mainUrl + "/auth/login";
        refreshUrl = mainUrl + "/auth/refresh";
        logoutUrl = mainUrl + "/auth/logout";

        userUrl = mainUrl + "/user";
        adminUrl = mainUrl + "/admin";
    }

    // ------------------------------------------------------------
    // LOGIN TESTS
    // ------------------------------------------------------------

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest(
                USERNAME,
                USER_PASSWORD,
                props.getClientId(),
                props.getClientSecret()
        );

        Map<String, Object> body = webTestClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsKeys("access_token", "refresh_token");
    }

    @Test
    void login_wrong_password() {
        LoginRequest request = new LoginRequest(
                USERNAME,
                "wrongpassword",
                props.getClientId(),
                props.getClientSecret()
        );

        webTestClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_unknown_user() {
        LoginRequest request = new LoginRequest(
                "unknownuser",
                "whatever",
                props.getClientId(),
                props.getClientSecret()
        );

        webTestClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ------------------------------------------------------------
    // ADMIN LOGIN TEST
    // ------------------------------------------------------------

    @Test
    void admin_login_success() {
        LoginRequest request = new LoginRequest(
                ADMIN,
                ADMIN_PASSWORD,
                props.getClientId(),
                props.getClientSecret()
        );

        Map<String, Object> body = webTestClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsKeys("access_token", "refresh_token");
    }

    // ------------------------------------------------------------
    // REFRESH TOKEN TESTS
    // ------------------------------------------------------------

    @Test
    void refresh_success() {
        String refreshToken = loginAndGetRefresh(USERNAME, USER_PASSWORD);

        Map<String, Object> body = webTestClient.post()
                .uri(refreshUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "refreshToken", refreshToken,
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsKey("access_token");
    }

    @Test
    void refresh_wrong_token() {
        webTestClient.post()
                .uri(refreshUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "refresh_token", "invalid-token",
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ------------------------------------------------------------
    // LOGOUT TESTS
    // ------------------------------------------------------------

    @Test
    void logout_success() {
        String refreshToken = loginAndGetRefresh(USERNAME, USER_PASSWORD);

        webTestClient.post()
                .uri(logoutUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "refresh_token", refreshToken,
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logout_wrong_token() {
        Map<String, Object> body = webTestClient.post()
                .uri(logoutUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "refresh_token", "invalid-token",
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.get("data")).isNull();
        assertThat((String) body.get("message")).contains("invalid_token");
    }

    // ------------------------------------------------------------
    // PROTECTED RESOURCES TESTS
    // ------------------------------------------------------------

    @Test
    void access_protected_without_token_unauthorized() {
        webTestClient.get()
                .uri(adminUrl)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void user_cannot_access_admin_forbidden() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        webTestClient.get()
                .uri(adminUrl)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void admin_can_access_admin() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        webTestClient.get()
                .uri(adminUrl)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void user_can_access_user_resource() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        webTestClient.get()
                .uri(userUrl)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void admin_cannot_access_user_resource() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        webTestClient.get()
                .uri(userUrl)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private String loginAndGetAccess(String username, String password) {
        Map<String, Object> body = login(username, password);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("access_token");
    }

    private String loginAndGetRefresh(String username, String password) {
        Map<String, Object> body = login(username, password);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("refresh_token");
    }

    private Map<String, Object> login(String username, String password) {
        LoginRequest request = new LoginRequest(
                username,
                password,
                props.getClientId(),
                props.getClientSecret()
        );

        return webTestClient.post()
                .uri(loginUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }
}