package lt.satsyuk.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.satsyuk.api.dto.ApiResponse;
import lt.satsyuk.auth.KeycloakAuthService;
import lt.satsyuk.auth.dto.KeycloakTokenResponse;
import lt.satsyuk.auth.dto.LoginRequest;
import lt.satsyuk.auth.dto.LogoutRequest;
import lt.satsyuk.auth.dto.RefreshRequest;
import lt.satsyuk.exception.KeycloakAuthException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest req) {
        try {
            KeycloakTokenResponse tokens = authService.login(req);
            return ResponseEntity.ok(ApiResponse.ok(tokens));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(ex.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(ApiResponse.ErrorCode.UNAUTHORIZED.getCode(), ex.getKeycloakMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            KeycloakTokenResponse tokens = authService.refresh(req);
            return ResponseEntity.ok(ApiResponse.ok(tokens));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(ex.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(ApiResponse.ErrorCode.INVALID_GRANT.getCode(), ex.getKeycloakMessage()));
        }
    }

    // Keycloak 26 revoke: 200 OK даже при ошибке
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(@Valid @RequestBody LogoutRequest req) {
        try {
            authService.logout(req);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(ex.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(ApiResponse.ErrorCode.INVALID_TOKEN.getCode(), ex.getKeycloakMessage()));
        }
    }
}