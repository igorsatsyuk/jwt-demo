package lt.satsyuk.api.integrationtest;

import lt.satsyuk.api.dto.ApiResponse;
import lt.satsyuk.auth.KeycloakProperties;
import lt.satsyuk.auth.dto.LoginRequest;
import lt.satsyuk.auth.dto.LogoutRequest;
import lt.satsyuk.auth.dto.RefreshRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractIntegrationTest {

    @Autowired
    protected KeycloakProperties props;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected static final String USERNAME = "user";
    protected static final String USER_PASSWORD = "password";
    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASSWORD = "admin";
    protected static final String INVALID_GRANT = "invalid_grant";
    protected static final String INVALID_CLIENT = "invalid_client";
    protected static final String INVALID_TOKEN = "invalid_token";

    @LocalServerPort
    protected int port;

    protected String loginUrl;
    protected String refreshUrl;
    protected String logoutUrl;

    protected String userUrl;
    protected String adminUrl;

    @BeforeEach
    void initializeUrls() {
        String mainUrl = "http://localhost:" + port + "/api";
        loginUrl = mainUrl + "/auth/login";
        refreshUrl = mainUrl + "/auth/refresh";
        logoutUrl = mainUrl + "/auth/logout";

        userUrl = mainUrl + "/user";
        adminUrl = mainUrl + "/admin";
    }

    protected void assertErrorBody(ResponseEntity<ApiResponse<Object>> response, int expectedCode, String expectedMessage) {
        ApiResponse<Object> body = response.getBody();
        assertThat(body).as("Response body should not be null").isNotNull();
        assertThat(body.getCode()).as("Response code should match expected").isEqualTo(expectedCode);
        assertThat(body.getMessage()).as("Response message should match expected").isEqualTo(expectedMessage);
    }

    protected void assertErrorStatusAndBody(ResponseEntity<ApiResponse<Object>> response,
                                            HttpStatus expectedStatus,
                                            int expectedCode,
                                            String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertErrorBody(response, expectedCode, expectedMessage);
    }

    protected ResponseEntity<ApiResponse<Object>> requestPost(String url, Record body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Record> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    protected ResponseEntity<ApiResponse<Object>> requestGet(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isEmpty())
            headers.setBearerAuth(token);
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    protected ResponseEntity<ApiResponse<Object>> loginRequest(String username,
                                                               String password,
                                                               String clientId,
                                                               String clientSecret) {

        LoginRequest request = new LoginRequest(
                username,
                password,
                clientId,
                clientSecret
        );
        return requestPost(loginUrl, request);
    }

    protected ResponseEntity<ApiResponse<Object>> loginRequest(String username,
                                                               String password) {

        return loginRequest(username, password, props.getClientId(), props.getClientSecret());
    }

    protected Map<String, Object> loginAndGetData(String username, String password) {
        ResponseEntity<ApiResponse<Object>> response = loginRequest(username, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        return (Map<String, Object>) response.getBody().getData();
    }

    protected String loginAndGetAccess(String username, String password) {
        return (String) loginAndGetData(username, password).get("access_token");
    }

    protected String loginAndGetRefresh(String username, String password) {
        return (String) loginAndGetData(username, password).get("refresh_token");
    }

    protected ResponseEntity<ApiResponse<Object>> logoutRequest(String refreshToken,
                                                                String clientId,
                                                                String clientSecret) {
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken, clientId, clientSecret);
        return requestPost(logoutUrl, logoutRequest);
    }

    protected ResponseEntity<ApiResponse<Object>> logoutRequest(String refreshToken) {
        return logoutRequest(refreshToken, props.getClientId(), props.getClientSecret());
    }

    protected ResponseEntity<ApiResponse<Object>> refreshRequest(String refreshToken,
                                                                String clientId,
                                                                String clientSecret) {
        RefreshRequest request = new RefreshRequest(refreshToken, clientId, clientSecret);
        return requestPost(refreshUrl, request);
    }

    protected ResponseEntity<ApiResponse<Object>> refreshRequest(String refreshToken) {
        return refreshRequest(refreshToken, props.getClientId(), props.getClientSecret());
    }
}