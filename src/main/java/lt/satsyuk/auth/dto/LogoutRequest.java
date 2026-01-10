package lt.satsyuk.auth.dto;

public record LogoutRequest(
        String refreshToken,
        String clientId,
        String clientSecret
) {}