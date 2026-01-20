package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.auth.KeycloakProperties;
import lt.satsyuk.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class KeycloakIntegrationIT {

    @Autowired
    private KeycloakProperties props;

    private static final String USERNAME = "user";
    private static final String USER_PASSWORD = "password";

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String loginUrl;
    private String refreshUrl;
    private String logoutUrl;

    private String userUrl;
    private String adminUrl;

    @BeforeEach
    void setUp() {
        String mainUrl = "http://localhost:" + port + "/api";
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_unknown_user() {
        LoginRequest request = new LoginRequest(
                "unknownuser",
                "whatever",
                props.getClientId(),
                props.getClientSecret()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");

        assertThat(data).containsKeys("access_token", "refresh_token");
    }

    // ------------------------------------------------------------
    // REFRESH TOKEN TESTS
    // ------------------------------------------------------------

    @Test
    void refresh_success() {
        LoginRequest request = new LoginRequest(
                USERNAME,
                USER_PASSWORD,
                props.getClientId(),
                props.getClientSecret()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                loginEntity,
                Map.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> loginBody = loginResponse.getBody();
        Map<String, Object> loginData = (Map<String, Object>) loginBody.get("data");

        String refreshToken = (String) loginData.get("refresh_token");

        HttpEntity<Map<String, String>> refreshEntity =
                new HttpEntity<>(Map.of(
                        "refreshToken", refreshToken,
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ), headers);

        ResponseEntity<Map> refreshResponse = restTemplate.exchange(
                refreshUrl,
                HttpMethod.POST,
                refreshEntity,
                Map.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> refreshBody = refreshResponse.getBody();
        Map<String, Object> refreshData = (Map<String, Object>) refreshBody.get("data");

        assertThat(refreshData).containsKeys("access_token");
    }

    @Test
    void refresh_wrong_token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of(
                        "refreshToken", "invalid-token",
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                refreshUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------
    // LOGOUT TESTS
    // ------------------------------------------------------------

    @Test
    void logout_success() {
        LoginRequest request = new LoginRequest(
                USERNAME,
                USER_PASSWORD,
                props.getClientId(),
                props.getClientSecret()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                loginEntity,
                Map.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> loginBody = loginResponse.getBody();
        Map<String, Object> loginData = (Map<String, Object>) loginBody.get("data");

        String refreshToken = (String) loginData.get("refresh_token");

        HttpEntity<Map<String, String>> logoutEntity =
                new HttpEntity<>(Map.of(
                        "refreshToken", refreshToken,
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ), headers);

        ResponseEntity<Map> logoutResponse = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                logoutEntity,
                Map.class
        );

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logout_wrong_token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of(
                        "refreshToken", "invalid-token",
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();

        assertThat(body.get("data")).isNull();

        String message = (String) body.get("message");
        assertThat(message).contains("invalid_token");
    }

    // ------------------------------------------------------------
    // PROTECTED RESOURCES TESTS
    // ------------------------------------------------------------

    @Test
    void access_protected_without_token_unauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(adminUrl, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void user_cannot_access_admin_forbidden() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                adminUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_can_access_admin() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                adminUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void user_can_access_user_resource() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                userUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void admin_cannot_access_user_resource() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                userUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------

    private String loginAndGetAccess(String username, String password) {
        LoginRequest request = new LoginRequest(
                username,
                password,
                props.getClientId(),
                props.getClientSecret()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                loginUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");

        return (String) data.get("access_token");
    }
}