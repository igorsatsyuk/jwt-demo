package lt.satsyuk.auth;

public record LoginRequest(
        String username,
        String password,
        String clientId,
        String clientSecret
) {}