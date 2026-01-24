package lt.satsyuk.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import lt.satsyuk.auth.dto.KeycloakTokenResponse;
import lt.satsyuk.auth.dto.LoginRequest;
import lt.satsyuk.auth.dto.LogoutRequest;
import lt.satsyuk.auth.dto.RefreshRequest;
import lt.satsyuk.exception.KeycloakAuthException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class KeycloakAuthService {

    private final RestTemplate rest;
    private final KeycloakProperties props;

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter refreshSuccessCounter;
    private final Counter refreshFailureCounter;
    private final Counter logoutSuccessCounter;
    private final Counter logoutFailureCounter;

    public KeycloakAuthService(RestTemplate rest, KeycloakProperties props, MeterRegistry registry) {
        this.rest = rest;
        this.props = props;

        this.loginSuccessCounter = Counter.builder("auth.login")
                .tag("result", "success")
                .description("Successful login attempts")
                .register(registry);

        this.loginFailureCounter = Counter.builder("auth.login")
                .tag("result", "failure")
                .description("Failed login attempts")
                .register(registry);

        this.refreshSuccessCounter = Counter.builder("auth.refresh")
                .tag("result", "success")
                .description("Successful token refresh attempts")
                .register(registry);

        this.refreshFailureCounter = Counter.builder("auth.refresh")
                .tag("result", "failure")
                .description("Failed token refresh attempts")
                .register(registry);

        this.logoutSuccessCounter = Counter.builder("auth.logout")
                .tag("result", "success")
                .description("Successful logout attempts")
                .register(registry);

        this.logoutFailureCounter = Counter.builder("auth.logout")
                .tag("result", "failure")
                .description("Failed logout attempts")
                .register(registry);
    }

    // ------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------
    public KeycloakTokenResponse login(LoginRequest req) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", req.clientId());
        form.add("client_secret", req.clientSecret());
        form.add("grant_type", "password");

        // Required for offline refresh tokens
        form.add("scope", "offline_access");

        form.add("username", req.username());
        form.add("password", req.password());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        log.info("➡️  LOGIN request to Keycloak: user={}, clientId={}, realm={}",
                req.username(), req.clientId(), props.getRealm());

        try {
            ResponseEntity<KeycloakTokenResponse> response =
                    rest.postForEntity(props.getTokenUrl(), entity, KeycloakTokenResponse.class);

            log.info("⬅️  TOKEN response: status={}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new KeycloakAuthException(
                        "Empty response",
                        HttpStatus.BAD_REQUEST,
                        "invalid_grant"
                );
            }

            loginSuccessCounter.increment();
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            log.error("❌ LOGIN error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());

            loginFailureCounter.increment();
            throw new KeycloakAuthException(
                    "Login failed",
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    extractErrorMessage(ex.getResponseBodyAsString())
            );
        }
    }

    // ------------------------------------------------------------
    // REFRESH
    // ------------------------------------------------------------
    public KeycloakTokenResponse refresh(RefreshRequest req) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", req.clientId());
        form.add("client_secret", req.clientSecret());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", req.refreshToken());

        // Required for offline refresh tokens in Keycloak 26
        form.add("scope", "offline_access");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        log.info("➡️  REFRESH request to Keycloak: clientId={}, realm={}",
                req.clientId(), props.getRealm());

        try {
            ResponseEntity<KeycloakTokenResponse> response =
                    rest.postForEntity(props.getTokenUrl(), entity, KeycloakTokenResponse.class);

            log.info("⬅️  REFRESH response: status={}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new KeycloakAuthException(
                        "Empty response",
                        HttpStatus.BAD_REQUEST,
                        "invalid_grant"
                );
            }

            refreshSuccessCounter.increment();
            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            log.error("❌ REFRESH error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());

            refreshFailureCounter.increment();
            throw new KeycloakAuthException(
                    "Refresh failed",
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    extractErrorMessage(ex.getResponseBodyAsString())
            );
        }
    }

    // ------------------------------------------------------------
    // LOGOUT (REVOKE)
    // ------------------------------------------------------------
    public void logout(LogoutRequest req) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", req.clientId());
        form.add("client_secret", req.clientSecret());
        form.add("token", req.refreshToken());
        form.add("token_type_hint", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        log.info("➡️  REVOKE request to Keycloak: clientId={}, realm={}",
                req.clientId(), props.getRealm());

        try {
            ResponseEntity<String> response =
                    rest.postForEntity(props.getLogoutUrl(), entity, String.class);

            log.info("⬅️  REVOKE response: status={}, body={}",
                    response.getStatusCode(), response.getBody());

            if (response.getBody() != null && response.getBody().contains("invalid_token")) {
                logoutFailureCounter.increment();
                throw new KeycloakAuthException(
                        "invalid_token",
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        "invalid_token"
                );
            }

            logoutSuccessCounter.increment();

        } catch (HttpStatusCodeException ex) {
            log.error("❌ REVOKE error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());

            logoutFailureCounter.increment();
            throw new KeycloakAuthException(
                    "Logout failed",
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    extractErrorMessage(ex.getResponseBodyAsString())
            );
        }
    }

    // ------------------------------------------------------------
    // UNIFIED ERROR PARSER
    // ------------------------------------------------------------
    private String extractErrorMessage(String body) {
        if (body == null) return "invalid_grant";
        if (body.contains("invalid_token")) return "invalid_token";
        if (body.contains("invalid_grant")) return "invalid_grant";
        if (body.contains("invalid_client")) return "invalid_client";
        if (body.contains("not_allowed")) return "not_allowed";
        return "invalid_grant";
    }
}