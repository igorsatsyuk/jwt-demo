package lt.satsyuk.api.integrationtest;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractIntegrationTest {

    static KeycloakContainer keycloak;

    static {
        if (System.getenv("DOCKER_HOST") != null || System.getenv("DOCKER_SOCK") != null || isDockerAvailable()) {
            try {
                keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
                        .withRealmImportFile("keycloak/realm-export.json");
                keycloak.start();
            } catch (Exception e) {
                System.err.println("Failed to start KeycloakContainer: " + e.getMessage());
            }
        }
    }

    private static boolean isDockerAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("docker ps");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
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
