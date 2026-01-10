package lt.satsyuk.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    private String authServerUrl;
    private String realm;

    private String tokenUrl;
    private String logoutUrl;

    // опционально — дефолты
    private String clientId;
    private String clientSecret;
}