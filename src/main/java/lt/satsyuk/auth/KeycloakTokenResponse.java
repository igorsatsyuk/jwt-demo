package lt.satsyuk.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakTokenResponse(
        String access_token,
        String refresh_token,
        Long expires_in,
        Long refresh_expires_in,
        String token_type,
        String scope,
        String session_state
) {}