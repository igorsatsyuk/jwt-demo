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

import java.awt.*;
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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String loginUrl;
    private String refreshUrl;
    private String logoutUrl;

    @BeforeEach
    void setUp() {
        String mainUrl = "http://localhost:" + port + "/api/auth";
        loginUrl = mainUrl + "/login";
        refreshUrl = mainUrl + "/refresh";
        logoutUrl = mainUrl + "/logout";
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
    // REFRESH TOKEN TESTS
    // ------------------------------------------------------------

    @Test
    void refresh_success() {
        // 1. Login first
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

        // 2. Refresh
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
                        "refresh_token", "invalid-token",
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
        // 1. Login first
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

        // 2. Logout
        HttpEntity<Map<String, String>> logoutEntity =
                new HttpEntity<>(Map.of(
                        "refresh_token", refreshToken,
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
                        "refresh_token", "invalid-token",
                        "clientId", props.getClientId(),
                        "clientSecret", props.getClientSecret()
                ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        // Keycloak 26 ALWAYS returns 200 for revoke, even for invalid tokens
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();

        // ApiResponse.error(...) → data = null
        assertThat(body.get("data")).isNull();

        // message should contain "invalid_token"
        String message = (String) body.get("message");
        assertThat(message).contains("invalid_token");
    }
}