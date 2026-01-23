package lt.satsyuk.api.integrationtest;

import dasniko.testcontainers.keycloak.KeycloakContainer;

public class TestKeycloakContainer {
    
    private static KeycloakContainer instance;
    
    public static KeycloakContainer getInstance() {
        if (instance == null) {
            instance = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
                    .withRealmImportFile("keycloak/realm-export.json")
                    .withReuse(true);
            instance.start();
        }
        return instance;
    }
}
