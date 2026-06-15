package lt.satsyuk.security;

import jakarta.servlet.FilterChain;
import lt.satsyuk.auth.JsonAuthEntryPoint;
import lt.satsyuk.config.DpopProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DpopAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private DpopProofValidator validator;

    @Mock
    private JsonAuthEntryPoint authEntryPoint;

    @Mock
    private FilterChain filterChain;

    private DpopProperties properties;
    private DpopAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new DpopProperties();
        properties.setEnabled(true);
        filter = new DpopAuthenticationFilter(properties, validator, authEntryPoint);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsValidationWhenDpopIsDisabled() throws Exception {
        properties.setEnabled(false);
        SecurityContextHolder.getContext().setAuthentication(bearerAuthentication("token", Map.of()));
        MockHttpServletRequest request = request("DPoP token", "proof");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(validator, authEntryPoint);
    }

    @Test
    void skipsValidationWhenAuthenticationMissing() throws Exception {
        MockHttpServletRequest request = request("DPoP token", "proof");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(validator, authEntryPoint);
    }

    @Test
    void skipsValidationForRegularBearerTokenWithoutDpopBinding() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(bearerAuthentication("token", Map.of()));
        MockHttpServletRequest request = request("Bearer token", null);

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(validator, authEntryPoint);
    }

    @Test
    void rejectsBoundTokenWithoutDpopAuthorizationScheme() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                bearerAuthentication("token", Map.of("cnf", Map.of("jkt", "thumbprint")))
        );
        MockHttpServletRequest request = request("Bearer token", "proof");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(authEntryPoint).commence(any(), any(), argThat(ex ->
                ex instanceof InsufficientAuthenticationException
                        && ex.getMessage().contains("authorization scheme is required")));
        verify(filterChain, never()).doFilter(any(), any());
        verifyNoInteractions(validator);
    }

    @Test
    void rejectsWhenProofHeaderMissing() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                bearerAuthentication("token", Map.of("cnf", Map.of("jkt", "thumbprint")))
        );
        MockHttpServletRequest request = request("DPoP token", null);

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(authEntryPoint).commence(any(), any(), argThat(ex ->
                ex instanceof InsufficientAuthenticationException
                        && ex.getMessage().contains("proof is required")));
        verify(filterChain, never()).doFilter(any(), any());
        verifyNoInteractions(validator);
    }

    @Test
    void validatesProofAndContinuesWhenProofIsCorrect() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                bearerAuthentication("token", Map.of("cnf", Map.of("jkt", "thumbprint")))
        );
        MockHttpServletRequest request = request("DPoP token", "proof");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(validator).validate("GET", "http://localhost:8080/api/clients/1", "token", "proof", "thumbprint");
        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(authEntryPoint);
    }

    @Test
    void rejectsWhenProofValidationFails() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                bearerAuthentication("token", Map.of("cnf", Map.of("jkt", "thumbprint")))
        );
        MockHttpServletRequest request = request("DPoP token", "proof");
        doThrow(new DpopProofValidationException("bad proof"))
                .when(validator).validate(any(), any(), any(), any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(authEntryPoint).commence(any(), any(), argThat(ex ->
                ex instanceof InsufficientAuthenticationException
                        && ex.getMessage().contains("bad proof")));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void validatesJwtAuthenticationAndIncludesQueryStringInUri() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                jwtAuthentication("jwt-token", Map.of("cnf", Map.of("jkt", "thumbprint")))
        );
        MockHttpServletRequest request = request("DPoP jwt-token", "proof", "sort=desc");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(validator).validate("GET", "http://localhost:8080/api/clients/1?sort=desc",
                "jwt-token", "proof", "thumbprint");
        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(authEntryPoint);
    }

    @Test
    void validatesWithNullTokenAndExpectedJktForUnsupportedAuthenticationType() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT_GET"))
                )
        );
        MockHttpServletRequest request = request("DPoP token", "proof");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(validator).validate("GET", "http://localhost:8080/api/clients/1", null, "proof", null);
        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(authEntryPoint);
    }

    private MockHttpServletRequest request(String authorization, String dpopProof) {
        return request(authorization, dpopProof, null);
    }

    private MockHttpServletRequest request(String authorization, String dpopProof, String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients/1");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setQueryString(queryString);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (dpopProof != null) {
            request.addHeader("DPoP", dpopProof);
        }
        return request;
    }

    private BearerTokenAuthentication bearerAuthentication(String token, Map<String, Object> attributes) {
        Map<String, Object> principalAttributes = attributes.isEmpty()
                ? Map.of("sub", "user")
                : attributes;

        OAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                "user",
                principalAttributes,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT_GET"))
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                token,
                NOW.minusSeconds(60),
                NOW.plusSeconds(300)
        );
        return new BearerTokenAuthentication(principal, accessToken, principal.getAuthorities());
    }

    private JwtAuthenticationToken jwtAuthentication(String token, Map<String, Object> claims) {
        Map<String, Object> tokenClaims = claims.isEmpty()
                ? Map.of("sub", "user")
                : claims;

        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claims(existingClaims -> existingClaims.putAll(tokenClaims))
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(300))
                .build();

        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT_GET")));
    }
}
