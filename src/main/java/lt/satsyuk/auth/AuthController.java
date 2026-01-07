package lt.satsyuk.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final KeycloakAuthService keycloak;

    public AuthController(KeycloakAuthService keycloak) {
        this.keycloak = keycloak;
    }

    @PostMapping("/login")
    public ResponseEntity<KeycloakTokenResponse> login(@RequestBody AuthRequest req) {
        return ResponseEntity.ok(keycloak.login(req.username(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<KeycloakTokenResponse> refresh(
            @RequestParam("refreshToken") String refreshToken
    ) {
        return ResponseEntity.ok(keycloak.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestParam("refreshToken") String refreshToken
    ) {
        keycloak.logout(refreshToken);
        return ResponseEntity.ok().build();
    }
}