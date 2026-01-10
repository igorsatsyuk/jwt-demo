package lt.satsyuk.auth.dto;

public record RefreshRequest(
        String refreshToken,
        String clientId,
        String clientSecret
) {}