package lt.satsyuk.auth.dto;

public record LoginRequest(
        String username,
        String password,
        String clientId,
        String clientSecret
) {}