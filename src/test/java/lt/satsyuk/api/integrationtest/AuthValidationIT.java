package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.api.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MainApplication.class)
class AuthValidationIT extends AbstractIntegrationTest {

    @Test
    void login_emptyFields_badRequest() {
        ResponseEntity<ApiResponse<Object>> response = loginRequest(
                "",
                "",
                "",
                ""
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ApiResponse.ErrorCode.BAD_REQUEST.getCode());

        Set<String> expected = Set.of(
                "username: Username is required",
                "clientId: ClientId is required",
                "password: Password is required",
                "clientSecret: ClientSecret is required"
        );

        String msg = body.getMessage();
        Set<String> actual = Arrays.stream(msg.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void refresh_emptyBody_badRequest() {
        ResponseEntity<ApiResponse<Object>> response = refreshRequest(
                "",
                "",
                ""
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ApiResponse.ErrorCode.BAD_REQUEST.getCode());

        Set<String> expected = Set.of(
                "refreshToken: RefreshToken is required",
                "clientId: ClientId is required",
                "clientSecret: ClientSecret is required"
        );

        String msg = body.getMessage();
        Set<String> actual = Arrays.stream(msg.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
    }
}
