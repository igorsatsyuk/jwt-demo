package lt.satsyuk.api.integrationtest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import lt.satsyuk.MainApplication;
import lt.satsyuk.auth.dto.LoginRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class KeycloakNegativeIT {

    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String loginUrl;
    private String refreshUrl;
    private String logoutUrl;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        String wiremockUrl = "http://localhost:" + wireMockServer.port();
        String realm = "test-realm";

        registry.add("keycloak.auth-server-url", () -> wiremockUrl);
        registry.add("keycloak.realm", () -> realm);
        registry.add("keycloak.token-url", () -> wiremockUrl + "/realms/" + realm + "/protocol/openid-connect/token");
        registry.add("keycloak.logout-url", () -> wiremockUrl + "/realms/" + realm + "/protocol/openid-connect/revoke");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> wiremockUrl + "/realms/" + realm);
    }

    @BeforeEach
    void setUp() {
        String mainUrl = "http://localhost:" + port + "/api";
        loginUrl = mainUrl + "/auth/login";
        refreshUrl = mainUrl + "/auth/refresh";
        logoutUrl = mainUrl + "/auth/logout";

        wireMockServer.resetAll();
    }

    // ------------------------------------------------------------
    // KEYCLOAK CONNECTION FAILURES
    // ------------------------------------------------------------

    @Test
    void login_keycloak_unavailable_500() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal_server_error\"}")));

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
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

        // AuthController catches KeycloakAuthException and returns 401 UNAUTHORIZED
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_keycloak_timeout() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(30000) // 30 seconds delay
                        .withBody("{}")));

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
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

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void login_keycloak_malformed_response() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("invalid-json")));

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
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

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    // ------------------------------------------------------------
    // AUTHENTICATION ERRORS
    // ------------------------------------------------------------

    @Test
    void login_invalid_credentials() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}")));

        LoginRequest request = new LoginRequest(
                "user",
                "wrongpassword",
                "test-client",
                "test-secret"
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
    void login_account_disabled() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}")));

        LoginRequest request = new LoginRequest(
                "disabled-user",
                "password",
                "test-client",
                "test-secret"
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

        // AuthController catches KeycloakAuthException and returns 401 UNAUTHORIZED
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_invalid_client() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_client\",\"error_description\":\"Invalid client credentials\"}")));

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "wrong-client",
                "wrong-secret"
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
    // REFRESH TOKEN ERRORS
    // ------------------------------------------------------------

    @Test
    void refresh_invalid_token() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid refresh token\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of(
                "refreshToken", "invalid-refresh-token",
                "clientId", "test-client",
                "clientSecret", "test-secret"
        ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                refreshUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_expired_token() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Token expired\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of(
                "refreshToken", "expired-refresh-token",
                "clientId", "test-client",
                "clientSecret", "test-secret"
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
    // LOGOUT/REVOKE ERRORS
    // ------------------------------------------------------------

    @Test
    void logout_invalid_token() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/revoke"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_token\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of(
                "refreshToken", "invalid-token",
                "clientId", "test-client",
                "clientSecret", "test-secret"
        ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKey("message");
        assertThat(body.get("message").toString()).contains("invalid_token");
    }

    @Test
    void logout_keycloak_unavailable() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/revoke"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service_unavailable\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of(
                "refreshToken", "some-token",
                "clientId", "test-client",
                "clientSecret", "test-secret"
        ), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );

        // AuthController catches KeycloakAuthException and returns 200 OK for logout
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKey("message");
    }

    // ------------------------------------------------------------
    // NETWORK FAILURES
    // ------------------------------------------------------------

    @Test
    void login_network_error() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
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

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void login_empty_response() {
        stubFor(post(urlPathMatching("/realms/.*/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(204))); // 204 No Content - truly empty response

        LoginRequest request = new LoginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
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

        // 204 No Content triggers KeycloakAuthException, caught by AuthController -> 401
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
