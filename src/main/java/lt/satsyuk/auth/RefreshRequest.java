package lt.satsyuk.auth;

public record RefreshRequest(
        String refreshToken,
        String clientId,
        String clientSecret
) {}