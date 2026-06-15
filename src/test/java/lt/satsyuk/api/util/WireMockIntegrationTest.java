package lt.satsyuk.api.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.security.RateLimitingFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class WireMockIntegrationTest extends AbstractIntegrationTest {

    protected WireMockIntegrationTest(KeycloakProperties props,
                                      CacheManager cacheManager,
                                      RateLimitingFilter rateLimitingFilter) {
        super(props, cacheManager, rateLimitingFilter);
    }

    protected static final String REALMS_PROTOCOL_OPENID_CONNECT_TOKEN = "/realms/.*/protocol/openid-connect/token";
    protected static final String REALMS_PROTOCOL_OPENID_CONNECT_LOGOUT = "/realms/.*/protocol/openid-connect/logout";
    protected static final String REALMS_PROTOCOL_OPENID_CONNECT_INTROSPECT = "/realms/.*/protocol/openid-connect/token/introspect";

    protected static WireMockServer wireMockServer;
    protected static final String REALM = "test-realm";

    private static String base;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .dynamicPort()
                        .extensions(new UserTokenTransformerV2())
        );
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        base = "http://localhost:" + wireMockServer.port();
        r.add("keycloak.auth-server-url", () -> base);
        r.add("keycloak.realm", () -> REALM);
        r.add("keycloak.token-url", () -> base + "/realms/" + REALM + "/protocol/openid-connect/token");
        r.add("keycloak.logout-url", () -> base + "/realms/" + REALM + "/protocol/openid-connect/logout");
        r.add("keycloak.introspection-url", () -> base + "/realms/" + REALM + "/protocol/openid-connect/token/introspect");
        r.add("spring.security.oauth2.resourceserver.opaque-token.introspection-uri",
                () -> base + "/realms/" + REALM + "/protocol/openid-connect/token/introspect");
        r.add("spring.security.oauth2.resourceserver.opaque-token.client-id", () -> "resource-server");
        r.add("spring.security.oauth2.resourceserver.opaque-token.client-secret", () -> "resource-server-secret");
        r.add("keycloak.resource-client-id", () -> "resource-server");
        r.add("keycloak.resource-client-secret", () -> "resource-server-secret");
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();

        String issuer = base + "/realms/" + REALM;

        // Discovery
        stubFor(get(urlEqualTo("/realms/" + REALM + "/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "issuer": "%s",
                                  "token_endpoint": "%s/realms/%s/protocol/openid-connect/token",
                                  "introspection_endpoint": "%s/realms/%s/protocol/openid-connect/token/introspect"
                                }
                                """.formatted(
                                issuer,
                                base,
                                REALM,
                                base,
                                REALM
                        ))));

        // Token endpoint — трансформер обработает только если тест НЕ переопределил stubFor(...)
        stubFor(post(urlEqualTo("/realms/" + REALM + "/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withTransformers("user-token-transformer-v2")));

        // Introspection
        stubFor(post(urlEqualTo("/realms/" + REALM + "/protocol/openid-connect/token/introspect"))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "active": true,
                                  "username": "user",
                                  "client_id": "spring-app",
                                  "azp": "spring-app",
                                  "realm_access": {"roles": ["CLIENT_CREATE", "CLIENT_GET", "CLIENT_SEARCH"]},
                                  "resource_access": {"spring-app": {"roles": ["CLIENT_CREATE", "CLIENT_GET", "CLIENT_SEARCH"]}}
                                }
                                """)));

        // Logout
        stubFor(post(urlEqualTo("/realms/" + REALM + "/protocol/openid-connect/logout"))
                .willReturn(aResponse().withStatus(204)));
    }

    // -------------------------------------------------------------------------
    // TRANSFORMER — НЕ ЛЕЗЕТ В ЧУЖИЕ МОКИ
    // -------------------------------------------------------------------------

    public static class UserTokenTransformerV2 implements ResponseTransformerV2 {

        @Override
        public String getName() {
            return "user-token-transformer-v2";
        }

        @Override
        public boolean applyGlobally() {
            return false; // критично: НЕ глобально!
        }

        @Override
        public Response transform(Response response, ServeEvent event) {
            Request req = event.getRequest();

            if (!req.getUrl().contains("/protocol/openid-connect/token"))
                return response;

            String body = req.getBodyAsString();
            String username = extract(body, "username");
            String password = extract(body, "password");

            String issuer = base + "/realms/" + REALM;

            try {
                if ("admin".equals(username) && "admin".equals(password)) {
                    return token(JwtTestUtil.generateToken("admin", "ADMIN", issuer));
                }
                if ("user".equals(username) && "password".equals(password)) {
                    return token(JwtTestUtil.generateToken("user", "USER", issuer));
                }
            } catch (Exception _) {
                return error(500, "token_generation_failed");
            }

            return error(400, "invalid_grant");
        }

        private Response token(String jwt) {
            return Response.response()
                    .status(200)
                    .headers(json())
                    .body("""
                            {
                              "access_token": "%s",
                              "refresh_token": "dummy"
                            }
                            """.formatted(jwt))
                    .build();
        }

        private Response error(int status, String msg) {
            return Response.response()
                    .status(status)
                    .headers(json())
                    .body("""
                            {"error":"%s"}
                            """.formatted(msg))
                    .build();
        }

        private HttpHeaders json() {
            return new HttpHeaders(new HttpHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));
        }

        private String extract(String body, String key) {
            if (body == null) return null;
            for (String p : body.split("&")) {
                if (p.startsWith(key + "=")) return p.substring((key + "=").length());
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // JWT UTIL
    // -------------------------------------------------------------------------

    public static class JwtTestUtil {

        private static final RSAKey KEY;
        private static final JWSSigner SIGNER;

        static {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair kp = gen.generateKeyPair();

                KEY = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) kp.getPublic())
                        .privateKey(kp.getPrivate())
                        .keyID(UUID.randomUUID().toString())
                        .algorithm(JWSAlgorithm.RS256)
                        .build();

                SIGNER = new RSASSASigner(KEY.toPrivateKey());

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static String getJwksJson() {
            return """
                    {"keys":[%s]}
                    """.formatted(KEY.toPublicJWK().toJSONString());
        }

        public static String generateToken(String username, String role, String issuer) throws JOSEException {

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issuer(issuer)
                    .expirationTime(Date.from(TestTime.FIXED_INSTANT.plusSeconds(300)))
                    .issueTime(Date.from(TestTime.FIXED_INSTANT))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("preferred_username", username)
                    .claim("azp", "spring-app")
                    .claim("typ", "Bearer")
                    .claim("scope", "email profile roles offline_access")
                    .claim("aud", List.of("account"))
                    .claim("realm_access", Map.of("roles", List.of(role)))
                    .claim("resource_access", Map.of(
                            "spring-app", Map.of("roles", List.of(role))
                    ))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(KEY.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims
            );

            jwt.sign(SIGNER);

            return jwt.serialize();
        }
    }
}
