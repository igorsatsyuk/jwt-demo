package lt.satsyuk.auth;

import lombok.RequiredArgsConstructor;
import lt.satsyuk.api.dto.ApiResponse;
import lt.satsyuk.auth.dto.KeycloakTokenResponse;
import lt.satsyuk.auth.dto.LoginRequest;
import lt.satsyuk.auth.dto.LogoutRequest;
import lt.satsyuk.auth.dto.RefreshRequest;
import lt.satsyuk.exception.KeycloakAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest req) {
        try {
            KeycloakTokenResponse tokens = authService.login(req);
            return ResponseEntity.ok(ApiResponse.ok(tokens));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40101, ex.getKeycloakMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@RequestBody RefreshRequest req) {
        try {
            KeycloakTokenResponse tokens = authService.refresh(req);
            return ResponseEntity.ok(ApiResponse.ok(tokens));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(40102, ex.getKeycloakMessage()));
        }
    }

    // Keycloak 26 revoke: 200 OK даже при ошибке → мы тоже отвечаем 200
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(@RequestBody LogoutRequest req) {
        try {
            authService.logout(req);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (KeycloakAuthException ex) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(40103, ex.getKeycloakMessage()));
        }
    }
}