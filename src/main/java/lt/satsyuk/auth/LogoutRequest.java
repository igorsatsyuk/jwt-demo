package lt.satsyuk.auth;

public record LogoutRequest(
        String refreshToken,
        String clientId,
        String clientSecret
) {}