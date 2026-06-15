package lt.satsyuk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.satsyuk.auth.JsonAuthEntryPoint;
import lt.satsyuk.config.DpopProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DpopAuthenticationFilter extends OncePerRequestFilter {

    private static final String DPOP_HEADER = "DPoP";
    private static final String DPOP_SCHEME = "DPoP ";
    private static final String CNF = "cnf";
    private static final String JKT = "jkt";

    private final DpopProperties properties;
    private final DpopProofValidator validator;
    private final JsonAuthEntryPoint authEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = extractAccessToken(authentication);
        Map<String, Object> tokenAttributes = extractTokenAttributes(authentication);
        String expectedJkt = extractExpectedJkt(tokenAttributes);

        String dpopProof = request.getHeader(DPOP_HEADER);
        boolean usesDpopScheme = usesDpopScheme(request);
        boolean tokenIsBoundToDpop = StringUtils.hasText(expectedJkt);

        if (!usesDpopScheme && !StringUtils.hasText(dpopProof) && !tokenIsBoundToDpop) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!usesDpopScheme) {
            reject(request, response, "DPoP authorization scheme is required");
            return;
        }

        if (!StringUtils.hasText(dpopProof)) {
            reject(request, response, "DPoP proof is required");
            return;
        }

        String requestUri = buildRequestUri(request);
        try {
            validator.validate(request.getMethod(), requestUri, accessToken, dpopProof, expectedJkt);
            filterChain.doFilter(request, response);
        } catch (DpopProofValidationException ex) {
            log.warn("DPoP validation failed: {}", ex.getMessage());
            reject(request, response, ex.getMessage());
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        authEntryPoint.commence(request, response, new InsufficientAuthenticationException(message));
    }

    private boolean usesDpopScheme(HttpServletRequest request) {
        Object originalAuthorization = request.getAttribute(DpopAwareBearerTokenResolver.ORIGINAL_AUTHORIZATION_ATTRIBUTE);
        String authorization = originalAuthorization instanceof String original
                ? original
                : request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, DPOP_SCHEME, 0, DPOP_SCHEME.length());
    }

    private String buildRequestUri(HttpServletRequest request) {
        StringBuilder requestUri = new StringBuilder(request.getRequestURL());
        if (StringUtils.hasText(request.getQueryString())) {
            requestUri.append('?').append(request.getQueryString());
        }
        return requestUri.toString();
    }

    private String extractAccessToken(Authentication authentication) {
        if (authentication instanceof BearerTokenAuthentication bearer) {
            return bearer.getToken().getTokenValue();
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        return null;
    }

    private Map<String, Object> extractTokenAttributes(Authentication authentication) {
        if (authentication instanceof BearerTokenAuthentication bearer) {
            return bearer.getTokenAttributes();
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getTokenAttributes();
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String extractExpectedJkt(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        Object cnf = attributes.get(CNF);
        if (cnf instanceof Map<?, ?> cnfMap) {
            Object jkt = cnfMap.get(JKT);
            if (jkt instanceof String thumbprint && StringUtils.hasText(thumbprint)) {
                return thumbprint;
            }
        }

        return null;
    }
}
