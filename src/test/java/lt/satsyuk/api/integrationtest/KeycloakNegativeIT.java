package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.api.util.WireMockIntegrationTest;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.dto.KeycloakTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import lt.satsyuk.security.RateLimitingFilter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class KeycloakNegativeIT extends WireMockIntegrationTest {

    KeycloakNegativeIT(@Qualifier("keycloakProperties") KeycloakProperties props,
                       CacheManager cacheManager,
                       RateLimitingFilter rateLimitingFilter) {
        super(props, cacheManager, rateLimitingFilter);
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

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(), INVALID_GRANT);
    }

    @Test
    void login_keycloak_timeout() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withFixedDelay(12_000) // longer than the test profile Keycloak client timeout
                        .withBody("{}")));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
    }

    @Test
    void login_keycloak_malformed_response() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("invalid-json")));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
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

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "wrongpassword",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
     }

    @Test
    void login_account_disabled() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}")));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "disabled-user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }

    @Test
    void login_invalid_client() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.UNAUTHORIZED.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_client\",\"error_description\":\"Invalid client credentials\"}")));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "wrong-client",
                "wrong-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
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

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = refreshRequest(
                "invalid-refresh-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.INVALID_GRANT.getCode(),
                INVALID_GRANT);
    }

    @Test
    void refresh_expired_token() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Token expired\"}")));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = refreshRequest(
                "expired-refresh-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.INVALID_GRANT.getCode(),
                INVALID_GRANT);
    }

    // ------------------------------------------------------------
    // LOGOUT/REVOKE ERRORS
    // ------------------------------------------------------------

    @Test
    void logout_invalid_token() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_LOGOUT))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"invalid_grant\"}")));

        ResponseEntity<AppResponse<Void>> response = logoutRequest(
                "invalid-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.INVALID_TOKEN.getCode(),
                INVALID_GRANT);
    }

    @Test
    void logout_keycloak_unavailable() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_LOGOUT))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("{\"error\":\"service_unavailable\"}")));

        ResponseEntity<AppResponse<Void>> response = logoutRequest(
                "some-token",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.SERVICE_UNAVAILABLE,
                AppResponse.ErrorCode.INVALID_TOKEN.getCode(),
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

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.INTERNAL_SERVER_ERROR,
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getDescription());
    }

    @Test
    void login_empty_response() {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NO_CONTENT.value()))); // truly empty response

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret"
        );

        assertErrorStatusAndBody(response, HttpStatus.BAD_REQUEST,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }
}
