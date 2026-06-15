package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.api.util.WireMockIntegrationTest;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.KeycloakTokenResponse;
import lt.satsyuk.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.CacheManager;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.security.RateLimitingFilter;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

    DpopIntegrationIT(@Qualifier("keycloakProperties") KeycloakProperties props,
                      CacheManager cacheManager,
                      RateLimitingFilter rateLimitingFilter,
                      ClientRepository clientRepository) {
        super(props, cacheManager, rateLimitingFilter);
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
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                    .build()
                    .toPublicJWK()
                    .computeThumbprint()
                    .toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
