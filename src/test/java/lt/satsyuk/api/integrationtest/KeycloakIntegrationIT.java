package lt.satsyuk.api.integrationtest;

import lt.satsyuk.auth.KeycloakTokenResponse;
import lt.satsyuk.auth.LoginRequest;
import lt.satsyuk.auth.LogoutRequest;
import lt.satsyuk.auth.RefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ImportAutoConfiguration(exclude = WebFluxAutoConfiguration.class)
class KeycloakIntegrationIT {

    @Autowired
    private WebTestClient web;

    private static final String CLIENT_ID = "spring-app";
    private static final String CLIENT_SECRET = "vYbuDDmT4ouy6vBn6ZzaEPkmaMSHfvab";

    @Test
    void loginAndAccessUserEndpoint() {
        LoginRequest login = new LoginRequest("user", "password", CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        web.get()
                .uri("/api/user")
                .header("Authorization", "Bearer " + token.access_token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("user endpoint");
    }

    @Test
    void adminCanAccessAdminEndpoint() {
        LoginRequest login = new LoginRequest("admin", "password", CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        web.get()
                .uri("/api/admin")
                .header("Authorization", "Bearer " + token.access_token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("admin endpoint");
    }

    @Test
    void userCannotAccessAdminEndpoint() {
        LoginRequest login = new LoginRequest("user", "password", CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        web.get()
                .uri("/api/admin")
                .header("Authorization", "Bearer " + token.access_token())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void refreshTokenFlow() {
        LoginRequest login = new LoginRequest("user", "password", CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        RefreshRequest refresh = new RefreshRequest(token.refresh_token(), CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse refreshed = web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refresh)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        web.get()
                .uri("/api/user")
                .header("Authorization", "Bearer " + refreshed.access_token())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logoutRevokesRefreshToken() {
        LoginRequest login = new LoginRequest("user", "password", CLIENT_ID, CLIENT_SECRET);

        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        LogoutRequest logout = new LogoutRequest(token.refresh_token(), CLIENT_ID, CLIENT_SECRET);

        web.post()
                .uri("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(logout)
                .exchange()
                .expectStatus().isOk();

        RefreshRequest refresh = new RefreshRequest(token.refresh_token(), CLIENT_ID, CLIENT_SECRET);

        web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refresh)
                .exchange()
                .expectStatus().is4xxClientError();
    }
}