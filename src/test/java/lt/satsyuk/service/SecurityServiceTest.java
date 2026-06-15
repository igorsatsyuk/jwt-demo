package lt.satsyuk.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private final SecurityService securityService = new SecurityService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientIdReturnsAnonymousWhenAuthIsNull() {
        assertThat(securityService.clientId()).isEqualTo(SecurityService.ANONYMOUS);
    }

    @Test
    void clientIdUsesAzpClaimForJwtAuthenticationToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "client-jwt")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(60))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityService.clientId()).isEqualTo("client-jwt");
    }

    @Test
    void clientIdUsesAzpAttributeForBearerTokenAuthentication() {
        BearerTokenAuthentication auth = bearerAuth(Map.of("azp", "client-azp"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityService.clientId()).isEqualTo("client-azp");
    }

    @Test
    void clientIdUsesClientIdFallbackForBearerTokenAuthentication() {
        BearerTokenAuthentication auth = bearerAuth(Map.of("client_id", "client-fallback"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityService.clientId()).isEqualTo("client-fallback");
    }

    @Test
    void clientIdReturnsAnonymousWhenAttributesMissing() {
        BearerTokenAuthentication auth = bearerAuth(Map.of(StandardClaimNames.SUB, "user"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityService.clientId()).isEqualTo(SecurityService.ANONYMOUS);
    }

    @Test
    void usernameReturnsAnonymousWhenAuthIsNull() {
        assertThat(securityService.username()).isEqualTo(SecurityService.ANONYMOUS);
    }

    @Test
    void usernameReturnsAuthenticationName() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("bob", "n/a");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(securityService.username()).isEqualTo("bob");
    }

    private static BearerTokenAuthentication bearerAuth(Map<String, Object> attributes) {
        OAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                attributes,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                NOW,
                NOW.plusSeconds(60)
        );
        return new BearerTokenAuthentication(principal, token, principal.getAuthorities());
    }
}

