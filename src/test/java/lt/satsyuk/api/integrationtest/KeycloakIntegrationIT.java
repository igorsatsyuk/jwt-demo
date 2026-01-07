package lt.satsyuk.api.integrationtest;

import lt.satsyuk.auth.AuthRequest;
import lt.satsyuk.auth.KeycloakTokenResponse;
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

    @Test
    void loginAndAccessUserEndpoint() {
        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "password"))
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
        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("admin", "password"))
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
        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "password"))
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
        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        KeycloakTokenResponse refreshed = web.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/refresh")
                        .queryParam("refreshToken", token.refresh_token())
                        .build())
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
        KeycloakTokenResponse token = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeycloakTokenResponse.class)
                .returnResult()
                .getResponseBody();

        web.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/logout")
                        .queryParam("refreshToken", token.refresh_token())
                        .build())
                .exchange()
                .expectStatus().isOk();

        web.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/refresh")
                        .queryParam("refreshToken", token.refresh_token())
                        .build())
                .exchange()
                .expectStatus().is4xxClientError();
    }
}