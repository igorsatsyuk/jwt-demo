package lt.satsyuk.auth.dto;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "RefreshToken is required")
        String refreshToken,
        @NotBlank(message = "ClientId is required")
        String clientId,
        @NotBlank(message = "ClientSecret is required")
        String clientSecret
) {}