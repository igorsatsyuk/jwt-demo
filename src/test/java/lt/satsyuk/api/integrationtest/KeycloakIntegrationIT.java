package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.api.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class KeycloakIntegrationIT extends KeycloakIntegrationTest {

    // ------------------------------------------------------------
    // LOGIN TESTS
    // ------------------------------------------------------------

    @Test
    void login_success() {
        Map<String, Object> data = loginAndGetData(USERNAME, USER_PASSWORD);
        assertThat(data).containsKeys("access_token", "refresh_token");
    }

    @Test
    void login_wrong_password() {
        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                USERNAME,
                "wrongpassword"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }

    @Test
    void login_unknown_user() {
        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "unknownuser",
                "whatever"
        );

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                INVALID_GRANT);
    }

    // ------------------------------------------------------------
    // ADMIN LOGIN TEST
    // ------------------------------------------------------------

    @Test
    void admin_login_success() {
        Map<String, Object> data = loginAndGetData(ADMIN, ADMIN_PASSWORD);
        assertThat(data).containsKeys("access_token", "refresh_token");
    }

    // ------------------------------------------------------------
    // REFRESH TOKEN TESTS
    // ------------------------------------------------------------

    @Test
    void refresh_success() {
        String refreshToken = loginAndGetRefresh(USERNAME, USER_PASSWORD);

        ResponseEntity<ApiResponse<Object>> refreshResponse = refreshRequest(refreshToken);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> refreshData = (Map<String, Object>) refreshResponse.getBody().getData();

        assertThat(refreshData).containsKeys("access_token", "refresh_token");
    }

    @Test
    void refresh_wrong_token() {
        ResponseEntity<ApiResponse<Object>> refreshResponse = refreshRequest("invalid-token");

        assertErrorStatusAndBody(refreshResponse, HttpStatus.BAD_REQUEST,
                ApiResponse.ErrorCode.INVALID_GRANT.getCode(),
                INVALID_GRANT);
    }

    // ------------------------------------------------------------
    // LOGOUT TESTS
    // ------------------------------------------------------------

    @Test
    void logout_success() {
        String refreshToken = loginAndGetRefresh(USERNAME, USER_PASSWORD);

        ResponseEntity<ApiResponse<Object>> logoutResponse = logoutRequest(refreshToken);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logout_wrong_token() {
        ResponseEntity<ApiResponse<Object>> response = logoutRequest("invalid-token");

        assertErrorStatusAndBody(response, HttpStatus.OK,
                ApiResponse.ErrorCode.INVALID_TOKEN.getCode(),
                INVALID_TOKEN);
    }

    // ------------------------------------------------------------
    // PROTECTED RESOURCES TESTS
    // ------------------------------------------------------------

    @Test
    void access_protected_without_token_unauthorized() {
        ResponseEntity<ApiResponse<Object>> response = requestGet(adminUrl, "");

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.ErrorCode.UNAUTHORIZED.getCode(),
                ApiResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void user_cannot_access_admin_forbidden() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<ApiResponse<Object>> response = requestGet(adminUrl, token);

        assertErrorStatusAndBody(response, HttpStatus.FORBIDDEN,
                ApiResponse.ErrorCode.FORBIDDEN.getCode(),
                ApiResponse.ErrorCode.FORBIDDEN.getDescription());
    }

    @Test
    void admin_can_access_admin() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        ResponseEntity<ApiResponse<Object>> response = requestGet(adminUrl, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String data = (String) response.getBody().getData();

        assertThat(data).isEqualTo("admin endpoint");
    }

    @Test
    void user_can_access_user_resource() {
        String token = loginAndGetAccess(USERNAME, USER_PASSWORD);

        ResponseEntity<ApiResponse<Object>> response = requestGet(userUrl, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String data = (String) response.getBody().getData();

        assertThat(data).isEqualTo("user endpoint");
    }

    @Test
    void admin_cannot_access_user_resource() {
        String token = loginAndGetAccess(ADMIN, ADMIN_PASSWORD);

        ResponseEntity<ApiResponse<Object>> response = requestGet(userUrl, token);

        assertErrorStatusAndBody(response, HttpStatus.FORBIDDEN,
                ApiResponse.ErrorCode.FORBIDDEN.getCode(),
                ApiResponse.ErrorCode.FORBIDDEN.getDescription());
    }
}