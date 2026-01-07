package lt.satsyuk.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    @Value("${keycloak.token-url}")
    private String tokenUrl;

    @Value("${keycloak.logout-url}")
    private String logoutUrl;

    private final WebClient webClient = WebClient.create();

    public KeycloakTokenResponse login(LoginRequest request) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", request.username());
        form.add("password", request.password());
        form.add("client_id", request.clientId());
        form.add("client_secret", request.clientSecret());

        return webClient.post()
                .uri(tokenUrl)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .block();
    }

    public KeycloakTokenResponse refresh(RefreshRequest request) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", request.refreshToken());
        form.add("client_id", request.clientId());
        form.add("client_secret", request.clientSecret());

        return webClient.post()
                .uri(tokenUrl)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .block();
    }

    public void logout(LogoutRequest request) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", request.refreshToken());
        form.add("client_id", request.clientId());
        form.add("client_secret", request.clientSecret());

        webClient.post()
                .uri(logoutUrl)
                .bodyValue(form)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}