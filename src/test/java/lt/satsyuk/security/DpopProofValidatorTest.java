package lt.satsyuk.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lt.satsyuk.config.DpopProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DpopProofValidatorTest {

    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String URI = "https://localhost:8443/api/clients/1";
    private static final String METHOD = "GET";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private DpopProperties properties;
    private DpopProofValidator validator;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        properties = new DpopProperties();
        properties.setEnabled(true);
        properties.setReplayCacheSize(100);
        properties.setMaxProofAge(Duration.ofSeconds(30));
        properties.setClockSkew(Duration.ofSeconds(1));

        validator = new DpopProofValidator(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        rsaKey = generateRsaKey();
    }

    @Test
    void validatesCorrectProof() throws Exception {
        String expectedJkt = rsaKey.toPublicJWK().computeThumbprint().toString();
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        assertThatCode(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, expectedJkt))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingProofHeader() {
        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, null, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("header is missing");
    }

    @Test
    void rejectsMissingAccessToken() {
        assertThatThrownBy(() -> validator.validate(METHOD, URI, "", "proof", null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("Access token is missing");
    }

    @Test
    void rejectsInvalidProofFormat() {
        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, "not-a-jwt", null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("format");
    }

    @Test
    void rejectsInvalidProofType() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                JOSEObjectType.JWT, true, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("proof type");
    }

    @Test
    void rejectsNoneAlgorithm() {
        SignedJWT jwt = mock(SignedJWT.class);
        JWSHeader header = mock(JWSHeader.class);
        when(jwt.getHeader()).thenReturn(header);
        when(header.getType()).thenReturn(new JOSEObjectType("dpop+jwt"));
        when(header.getAlgorithm()).thenReturn(JWSAlgorithm.parse("none"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(validator, "extractAndValidatePublicJwk", jwt))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("proof algorithm");
    }

    @Test
    void rejectsMissingJwk() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), false, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("JWK is missing");
    }

    @Test
    void rejectsMethodMismatch() throws Exception {
        String proof = createProof(rsaKey, "POST", URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("method mismatch");
    }

    @Test
    void rejectsUriMismatch() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI + "/mismatch", ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("URI mismatch");
    }

    @Test
    void rejectsMissingIssueTime() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, null, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("issue time is missing");
    }

    @Test
    void rejectsExpiredIssueTime() throws Exception {
        Instant expired = NOW
                .minus(properties.getMaxProofAge())
                .minus(properties.getClockSkew())
                .minusSeconds(2);
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, expired, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsMissingAthClaim() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, false);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, null))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("access token hash is missing");
    }

    @Test
    void rejectsAthMismatch() throws Exception {
        String expectedJkt = rsaKey.toPublicJWK().computeThumbprint().toString();
        String proof = createProof(rsaKey, METHOD, URI, "different-token", UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, expectedJkt))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("hash mismatch");
    }

    @Test
    void rejectsJktMismatch() throws Exception {
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, UUID.randomUUID().toString(),
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, "another-thumbprint"))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("key thumbprint mismatch");
    }

    @Test
    void rejectsReplayByJti() throws Exception {
        String expectedJkt = rsaKey.toPublicJWK().computeThumbprint().toString();
        String jti = UUID.randomUUID().toString();
        String proof = createProof(rsaKey, METHOD, URI, ACCESS_TOKEN, jti,
                new JOSEObjectType("dpop+jwt"), true, NOW, true);

        validator.validate(METHOD, URI, ACCESS_TOKEN, proof, expectedJkt);

        assertThatThrownBy(() -> validator.validate(METHOD, URI, ACCESS_TOKEN, proof, expectedJkt))
                .isInstanceOf(DpopProofValidationException.class)
                .hasMessageContaining("replay");
    }

    private String createProof(RSAKey key,
                               String method,
                               String uri,
                               String accessToken,
                               String jti,
                               JOSEObjectType proofType,
                               boolean includeJwk,
                               Instant issueTime,
                               boolean includeAth) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .claim("htm", method)
                .claim("htu", uri);
        if (issueTime != null) {
            claims.issueTime(Date.from(issueTime));
        }
        if (includeAth) {
            claims.claim("ath", ath(accessToken));
        }

        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(proofType);
        if (includeJwk) {
            header.jwk(key.toPublicJWK());
        }

        SignedJWT jwt = new SignedJWT(header.build(), claims.build());
        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }

    private static String ath(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return com.nimbusds.jose.util.Base64URL.encode(
                digest.digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        ).toString();
    }

    private static RSAKey generateRsaKey() throws JOSEException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception ex) {
            throw new JOSEException("Failed to generate test RSA key", ex);
        }
    }
}
