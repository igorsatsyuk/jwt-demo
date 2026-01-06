package lt.satsyuk.api.unittest;

import lt.satsyuk.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final String USER = "user";
    private static final String ROLE = "USER";
    private static final String PASSWORD = "password";
    private static final String SECRET_KEY = "super-secret-key-which-is-long-enough-1234567890";
    private static final long JWT_EXPIRATION_MS = 1000 * 60 * 15;
    private static final long JWT_REFRESH_EXPIRATION_MS = 1000 * 60 * 60 * 24;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET_KEY,
                JWT_EXPIRATION_MS,
                JWT_REFRESH_EXPIRATION_MS);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        var user = User.withUsername(USER)
                .password(PASSWORD)
                .roles(ROLE)
                .build();

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo(USER);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void testInvalidToken() {
        var user = User.withUsername(USER)
                .password(PASSWORD)
                .roles(ROLE)
                .build();

        assertThat(jwtService.isTokenValid("invalid.token.value", user)).isFalse();
    }
}