package lt.satsyuk.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService authService;

    @PostMapping("/login")
    public KeycloakTokenResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public KeycloakTokenResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}