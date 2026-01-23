package lt.satsyuk.api.integrationtest;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractIntegrationTest {

    static KeycloakContainer keycloak = TestKeycloakContainer.getInstance();

    @BeforeAll
    static void checkDockerAvailable() {
        try {
            assumeTrue(keycloak.isRunning(), "Keycloak container should be running");
        } catch (Exception e) {
            assumeTrue(false, "Docker is not available. Please install Docker Desktop for Windows and ensure it's running.");
        }
    }

    @DynamicPropertySource
    static void registerResourceServerIssuerProperty(DynamicPropertyRegistry registry) {
        if (keycloak != null && keycloak.isRunning()) {
            String authServerUrl = keycloak.getAuthServerUrl();
            String realm = "my-realm";

            registry.add("keycloak.auth-server-url", () -> authServerUrl);
            registry.add("keycloak.realm", () -> realm);
            registry.add("keycloak.token-url", () -> authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token");
            registry.add("keycloak.logout-url", () -> authServerUrl + "/realms/" + realm + "/protocol/openid-connect/revoke");
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    () -> authServerUrl + "/realms/" + realm);
        }
    }
}
