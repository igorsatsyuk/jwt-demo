package lt.satsyuk.auth.dto;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "ClientId is required")
        String clientId,
        @NotBlank(message = "ClientSecret is required")
        String clientSecret
) {}