package lt.satsyuk.api.integrationtest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lt.satsyuk.auth.AuthRequest;
import lt.satsyuk.auth.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthFlowIT {

    @Autowired
    private WebTestClient web;

    private static final String SECRET = "super-secret-key-which-is-long-enough-1234567890";

    @Test
    void loginAndAccessProtected() {
        var req = new AuthRequest("user", "password");

        var loginResp = web.post()
                .uri("/auth/login")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(loginResp).isNotNull();

        web.get()
                .uri("/api/protected")
                .header("Authorization", "Bearer " + loginResp.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("protected content");
    }

    @Test
    void loginAndAccessAdmin() {
        var req = new AuthRequest("admin", "password");

        var loginResp = web.post()
                .uri("/auth/login")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(loginResp).isNotNull();

        web.get()
                .uri("/api/admin")
                .header("Authorization", "Bearer " + loginResp.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("admin content");
    }

    @Test
    void refreshWithInvalidToken() {
        web.post()
                .uri("/auth/refresh?refreshToken=not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshWithForgedToken() {
        String forged = Jwts.builder()
                .setSubject("user")
                .signWith(Keys.hmacShaKeyFor("another-secret-key-which-is-long-enough-123".getBytes()))
                .compact();

        web.post()
                .uri("/auth/refresh?refreshToken=" + forged)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshWithExpiredToken() {
        String expired = Jwts.builder()
                .setSubject("user")
                .setIssuedAt(new Date(System.currentTimeMillis() - 100000))
                .setExpiration(new Date(System.currentTimeMillis() - 50000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();

        web.post()
                .uri("/auth/refresh?refreshToken=" + expired)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshWithTokenForAnotherUser() {
        String wrongUserToken = Jwts.builder()
                .setSubject("hacker")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();

        web.post()
                .uri("/auth/refresh?refreshToken=" + wrongUserToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshWithEmptyToken() {
        web.post()
                .uri("/auth/refresh?refreshToken=")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void refreshWithoutToken() {
        web.post()
                .uri("/auth/refresh")
                .exchange()
                .expectStatus().isBadRequest();
    }
}