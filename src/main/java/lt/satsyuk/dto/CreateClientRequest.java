package lt.satsyuk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateClientRequest(

        @Schema(description = "Idempotency key. When provided, it is used as the Request id instead of generating a new one.")
        UUID idempotencyKey,

        @NotBlank(message = "{validation.firstName.required}")
        @Size(max = 100)
        @Schema(example = "John")
        String firstName,

        @NotBlank(message = "{validation.lastName.required}")
        @Size(max = 100)
        @Schema(example = "Doe")
        String lastName,

        @NotBlank(message = "{validation.phone.required}")
        @Pattern(regexp = "\\+?\\d{7,15}", message = "{validation.phone.invalid}")
        @Schema(example = "+37060000000")
        String phone
) {}