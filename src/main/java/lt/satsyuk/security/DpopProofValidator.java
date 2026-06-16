package lt.satsyuk.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lt.satsyuk.config.DpopProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

@Component
public class DpopProofValidator {

    private static final String PROOF_TYPE = "dpop+jwt";
    private static final String HTTP_METHOD_CLAIM = "htm";
    private static final String HTTP_URI_CLAIM = "htu";
    private static final String ACCESS_TOKEN_HASH_CLAIM = "ath";
    private static final String SHA_256 = "SHA-256";
    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    private final DpopProperties properties;
    private final Clock clock;
    private final Cache<String, Instant> usedProofIds;

    public DpopProofValidator(DpopProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.usedProofIds = Caffeine.newBuilder()
                .maximumSize(properties.getReplayCacheSize())
                .expireAfterWrite(properties.getMaxProofAge().plus(properties.getClockSkew()))
                .build();
    }

    public void validate(String requestMethod,
                         String requestUri,
                         String accessToken,
                         String dpopProof,
                         String expectedJkt) {
        if (!StringUtils.hasText(dpopProof)) {
            throw new DpopProofValidationException("DPoP proof header is missing");
        }
        if (!StringUtils.hasText(accessToken)) {
            throw new DpopProofValidationException("Access token is missing for DPoP validation");
        }

        SignedJWT jwt = parseSignedJwt(dpopProof);
        JWK publicJwk = extractAndValidatePublicJwk(jwt);
        verifySignature(jwt, publicJwk);
        validateClaims(jwt, requestMethod, requestUri, accessToken);

        if (StringUtils.hasText(expectedJkt)) {
            validateJktBinding(publicJwk, expectedJkt);
        }
    }

    private SignedJWT parseSignedJwt(String dpopProof) {
        try {
            return SignedJWT.parse(dpopProof);
        } catch (Exception _) {
            throw new DpopProofValidationException("Invalid DPoP proof format");
        }
    }

    private JWK extractAndValidatePublicJwk(SignedJWT jwt) {
        String typ = jwt.getHeader().getType() != null ? jwt.getHeader().getType().toString() : null;
        if (!PROOF_TYPE.equalsIgnoreCase(typ)) {
            throw new DpopProofValidationException("Invalid DPoP proof type");
        }

        if (jwt.getHeader().getAlgorithm() == null || "none".equalsIgnoreCase(jwt.getHeader().getAlgorithm().getName())) {
            throw new DpopProofValidationException("Invalid DPoP proof algorithm");
        }

        JWK headerJwk = jwt.getHeader().getJWK();
        if (headerJwk == null) {
            throw new DpopProofValidationException("DPoP proof JWK is missing");
        }

        JWK publicJwk = headerJwk.toPublicJWK();
        if (publicJwk == null) {
            throw new DpopProofValidationException("DPoP proof public JWK is missing");
        }

        return publicJwk;
    }

    private void verifySignature(SignedJWT jwt, JWK publicJwk) {
        try {
            JWSVerifier verifier = createVerifier(publicJwk);
            if (!jwt.verify(verifier)) {
                throw new DpopProofValidationException("Invalid DPoP proof signature");
            }
        } catch (JOSEException _) {
            throw new DpopProofValidationException("Unable to verify DPoP proof signature");
        }
    }

    private JWSVerifier createVerifier(JWK publicJwk) throws JOSEException {
        if (publicJwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        }

        if (publicJwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey);
        }

        if (publicJwk instanceof OctetKeyPair octetKeyPair && Curve.Ed25519.equals(octetKeyPair.getCurve())) {
            return new Ed25519Verifier(octetKeyPair);
        }

        throw new JOSEException("Unsupported DPoP JWK key type");
    }

    private void validateClaims(SignedJWT jwt,
                                String requestMethod,
                                String requestUri,
                                String accessToken) {
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            validateRequestMethod(claims, requestMethod);
            validateRequestUri(claims, requestUri);
            validateIssueTime(claims);
            validateProofId(claims);
            validateAccessTokenHash(claims, accessToken);
        } catch (DpopProofValidationException ex) {
            throw ex;
        } catch (Exception _) {
            throw new DpopProofValidationException("Invalid DPoP proof claims");
        }
    }

    private void validateRequestMethod(JWTClaimsSet claims, String requestMethod) {
        String htm = stringClaim(claims, HTTP_METHOD_CLAIM);
        if (!StringUtils.hasText(htm) || !htm.equalsIgnoreCase(requestMethod)) {
            throw new DpopProofValidationException("DPoP proof method mismatch");
        }
    }

    private void validateRequestUri(JWTClaimsSet claims, String requestUri) {
        String htu = stringClaim(claims, HTTP_URI_CLAIM);
        if (!StringUtils.hasText(htu)) {
            throw new DpopProofValidationException("DPoP proof URI is missing");
        }

        String expected = normalizeUri(requestUri);
        String actual = normalizeUri(htu);
        if (!expected.equals(actual)) {
            throw new DpopProofValidationException("DPoP proof URI mismatch");
        }
    }

    private void validateIssueTime(JWTClaimsSet claims) {
        Instant iat = claims.getIssueTime() != null ? claims.getIssueTime().toInstant() : null;
        if (iat == null) {
            throw new DpopProofValidationException("DPoP proof issue time is missing");
        }

        Instant now = Instant.now(clock);
        Instant notBefore = now.minus(properties.getMaxProofAge()).minus(properties.getClockSkew());
        Instant notAfter = now.plus(properties.getClockSkew());

        if (iat.isBefore(notBefore) || iat.isAfter(notAfter)) {
            throw new DpopProofValidationException("DPoP proof is expired or issued in the future");
        }
    }

    private void validateProofId(JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (!StringUtils.hasText(jti)) {
            throw new DpopProofValidationException("DPoP proof ID is missing");
        }

        if (usedProofIds.getIfPresent(jti) != null) {
            throw new DpopProofValidationException("DPoP proof replay detected");
        }
        usedProofIds.put(jti, Instant.now(clock));
    }

    private void validateAccessTokenHash(JWTClaimsSet claims, String accessToken) {
        String ath = stringClaim(claims, ACCESS_TOKEN_HASH_CLAIM);
        if (!StringUtils.hasText(ath)) {
            throw new DpopProofValidationException("DPoP proof access token hash is missing");
        }

        MessageDigest digest = createSha256Digest();
        byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        String expectedAth = com.nimbusds.jose.util.Base64URL.encode(hash).toString();

        if (!MessageDigest.isEqual(
                expectedAth.getBytes(StandardCharsets.US_ASCII),
                ath.getBytes(StandardCharsets.US_ASCII))) {
            throw new DpopProofValidationException("DPoP proof access token hash mismatch");
        }
    }

    private void validateJktBinding(JWK publicJwk, String expectedJkt) {
        try {
            String actualJkt = publicJwk.computeThumbprint().toString();
            if (!MessageDigest.isEqual(
                    actualJkt.getBytes(StandardCharsets.US_ASCII),
                    expectedJkt.getBytes(StandardCharsets.US_ASCII))) {
                throw new DpopProofValidationException("DPoP proof key thumbprint mismatch");
            }
        } catch (JOSEException _) {
            throw new DpopProofValidationException("Unable to compute DPoP proof key thumbprint");
        }
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String normalizeUri(String rawUri) {
        URI uri = URI.create(rawUri);

        String scheme = uri.getScheme();
        if (!StringUtils.hasText(scheme)) {
            throw new DpopProofValidationException("URI scheme is required for DPoP validation");
        }
        String normalizedScheme = scheme.toLowerCase();

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new DpopProofValidationException("URI host is required for DPoP validation");
        }
        String normalizedHost = host.toLowerCase();

        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (HTTPS.equals(normalizedScheme) && port == 443)
                || (HTTP.equals(normalizedScheme) && port == 80);
        String authority = defaultPort ? normalizedHost : normalizedHost + ":" + port;

        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        String query = uri.getRawQuery();

        if (query == null) {
            return normalizedScheme + "://" + authority + path;
        }

        return normalizedScheme + "://" + authority + path + "?" + query;
    }

    private String stringClaim(JWTClaimsSet claims, String claimName) {
        Object claimValue = claims.getClaim(claimName);
        if (claimValue instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }
}
