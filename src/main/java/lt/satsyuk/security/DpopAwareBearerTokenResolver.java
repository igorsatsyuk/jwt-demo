package lt.satsyuk.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.StringUtils;

public class DpopAwareBearerTokenResolver implements BearerTokenResolver {

    public static final String ORIGINAL_AUTHORIZATION_ATTRIBUTE =
            DpopAwareBearerTokenResolver.class.getName() + ".originalAuthorization";
    private static final String DPOP_PREFIX = "DPoP ";
    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, DPOP_PREFIX, 0, DPOP_PREFIX.length())) {
            request.setAttribute(ORIGINAL_AUTHORIZATION_ATTRIBUTE, authorization);
            String token = authorization.substring(DPOP_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }

        return delegate.resolve(request);
    }
}
