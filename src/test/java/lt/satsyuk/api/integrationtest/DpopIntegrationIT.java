package lt.satsyuk.api.integrationtest;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lt.satsyuk.MainApplication;
import lt.satsyuk.api.util.WireMockIntegrationTest;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.KeycloakTokenResponse;
import lt.satsyuk.model.Client;
import lt.satsyuk.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.cache.CacheManager;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.security.RateLimitingFilter;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SpringBootTest(
        classes = MainApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DpopIntegrationIT extends WireMockIntegrationTest {

    private final ClientRepository clientRepository;
    private final Clock clock;

    DpopIntegrationIT(@Qualifier("keycloakProperties") KeycloakProperties props,
                      CacheManager cacheManager,
                      RateLimitingFilter rateLimitingFilter,
                      ClientRepository clientRepository,
                      Clock clock) {
        super(props, cacheManager, rateLimitingFilter);
        this.clientRepository = clientRepository;
        this.clock = clock;
    }

    @Test
    void login_forwardsDpopHeaderToKeycloak() {
        String dpopProof = "proof-value";

        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .withHeader("DPoP", equalTo(dpopProof))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "access_token": "access",
                                  "refresh_token": "refresh",
                                  "token_type": "DPoP"
                                }
                                """)));

        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(
                "user",
                "password",
                "test-client",
                "test-secret",
                dpopProof
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().getTokenType()).isEqualTo("DPoP");
        verify(postRequestedFor(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_TOKEN))
                .withHeader("DPoP", equalTo(dpopProof)));
    }

    @Test
    void dpopBoundToken_withoutProof_isUnauthorized() {
        String accessToken = "bound-access-token";
        String jkt = randomJkt();
        stubIntrospectionWithJkt(jkt);

        ResponseEntity<AppResponse<Object>> response = requestGet(clientUrl + "/1", accessToken);

        assertErrorStatusAndBody(response, HttpStatus.UNAUTHORIZED,
                AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                AppResponse.ErrorCode.UNAUTHORIZED.getDescription());
    }

    @Test
    void dpopBoundToken_withValidProofAndAuthorizationScheme_returnsOk() throws Exception {
        Client client = clientRepository.save(Client.builder()
                .firstName("Dpop")
                .lastName("Client")
                .phone("+3706" + Math.abs(UUID.randomUUID().hashCode() % 10_000_000))
                .build());

        String accessToken = "bound-access-token";
        RSAKey key = generateRsaKey();
        String jkt = key.toPublicJWK().computeThumbprint().toString();
        stubIntrospectionWithJkt(jkt);

        String targetUri = clientUrl + "/" + client.getId();
        String dpopProof = createProof(key, "GET", targetUri, accessToken, UUID.randomUUID().toString());

        ResponseEntity<AppResponse<Object>> response = requestGetDpop(
                targetUri,
                accessToken,
                dpopProof,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isZero();
        assertThat(response.getBody().data()).isNotNull();
    }

    private void stubIntrospectionWithJkt(String jkt) {
        stubFor(post(urlPathMatching(REALMS_PROTOCOL_OPENID_CONNECT_INTROSPECT))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "active": true,
                                  "username": "user",
                                  "client_id": "spring-app",
                                  "azp": "spring-app",
                                  "cnf": {"jkt": "%s"},
                                  "realm_access": {"roles": ["CLIENT_GET"]},
                                  "resource_access": {"spring-app": {"roles": ["CLIENT_GET"]}}
                                }
                                """.formatted(jkt))));
    }

    private String randomJkt() {
        try {
            RSAKey key = generateRsaKey();
            return key.toPublicJWK().computeThumbprint().toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String createProof(RSAKey key,
                               String method,
                               String uri,
                               String accessToken,
                               String jti) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .issueTime(Date.from(clock.instant()))
                .claim("htm", method)
                .claim("htu", uri)
                .claim("ath", ath(accessToken))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("dpop+jwt"))
                        .jwk(key.toPublicJWK())
                        .build(),
                claims
        );

        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }

    private String ath(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return com.nimbusds.jose.util.Base64URL
                .encode(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .toString();
    }

    private RSAKey generateRsaKey() {
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
            throw new IllegalStateException(ex);
        }
    }
}
