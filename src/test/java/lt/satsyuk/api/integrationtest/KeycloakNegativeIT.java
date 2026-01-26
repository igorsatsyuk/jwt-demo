package lt.satsyuk.api.integrationtest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import lt.satsyuk.MainApplication;
import lt.satsyuk.api.dto.ApiResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class KeycloakNegativeIT extends AbstractIntegrationTest {

    public static final String REALMS_PROTOCOL_OPENID_CONNECT_TOKEN = "/realms/.*/protocol/openid-connect/token";
    public static final String REALMS_PROTOCOL_OPENID_CONNECT_REVOKE = "/realms/.*/protocol/openid-connect/revoke";
    private static WireMockServer wireMockServer;

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
        wireMockServer.resetAll();
    }

    // ------------------------------------------------------------
    // KEYCLOAK CONNECTION FAILURES
    // ------------------------------------------------------------

    @Test
    void login_keycloak_unavailable_500() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"internal_server_error\"}")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(), INVALID_GRANT);
    }

    @Test
    void login_keycloak_timeout() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withFixedDelay(3000) // 3 seconds delay
                        .withBody("{}")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
    }

    @Test
    void login_keycloak_malformed_response() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("invalid-json")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
    }

    // ------------------------------------------------------------
    // AUTHENTICATION ERRORS
    // ------------------------------------------------------------

    @Test
    void login_invalid_credentials() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.UNAUTHORIZED.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "wrongpassword",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
     }

    @Test
    void login_account_disabled() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "disabled-user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }

    @Test
    void login_invalid_client() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.UNAUTHORIZED.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_client\",\"error_description\":\"Invalid client credentials\"}")));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "wrong-client",
                "wrong-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_CLIENT);
    }

    // ------------------------------------------------------------
    // REFRESH TOKEN ERRORS
    // ------------------------------------------------------------

    @Test
    void refresh_invalid_token() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid refresh token\"}")));

        ResponseEntity<ApiResponse<Object>> response = refreshRequest(
                "invalid-refresh-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                ApiResponse.ErrorCode.INVALID_GRANT.getCode(),
                INVALID_GRANT);
    }

    @Test
    void refresh_expired_token() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Token expired\"}")));

        ResponseEntity<ApiResponse<Object>> response = refreshRequest(
                "expired-refresh-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                ApiResponse.ErrorCode.INVALID_GRANT.getCode(),
                INVALID_GRANT);
    }

    // ------------------------------------------------------------
    // LOGOUT/REVOKE ERRORS
    // ------------------------------------------------------------

    @Test
    void logout_invalid_token() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_REVOKE))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_token\"}")));

        ResponseEntity<ApiResponse<Object>> response = logoutRequest(
                "invalid-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.OK,
                ApiResponse.ErrorCode.INVALID_TOKEN.getCode(),
                INVALID_TOKEN);
    }

    @Test
    void logout_keycloak_unavailable() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_REVOKE))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"service_unavailable\"}")));

        ResponseEntity<ApiResponse<Object>> response = logoutRequest(
                "some-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.SERVICE_UNAVAILABLE,
                ApiResponse.ErrorCode.INVALID_TOKEN.getCode(),
                INVALID_GRANT);
    }

    // ------------------------------------------------------------
    // NETWORK FAILURES
    // ------------------------------------------------------------

    @Test
    void login_network_error() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ApiResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
    }

    @Test
    void login_empty_response() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NO_CONTENT.value()))); // truly empty response

        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }
}
