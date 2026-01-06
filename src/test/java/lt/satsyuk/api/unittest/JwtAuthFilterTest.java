package lt.satsyuk.api.unittest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lt.satsyuk.config.JwtAuthFilter;
import lt.satsyuk.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsService.class);
        filter = new JwtAuthFilter(jwtService, userDetailsService);
    }

    @Test
    void testAuthEndpointsAreSkipped() throws Exception {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);

        when(request.getServletPath()).thenReturn("/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void testValidTokenAuthenticatesUser() throws Exception {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);

        when(request.getServletPath()).thenReturn("/api/protected");
        when(request.getHeader("Authorization")).thenReturn("Bearer token123");
        when(jwtService.extractUsername("token123")).thenReturn("user");

        var user = User.withUsername("user").password("pass").roles("USER").build();
        when(userDetailsService.loadUserByUsername("user")).thenReturn(user);
        when(jwtService.isTokenValid("token123", user)).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verify(jwtService).extractUsername("token123");
    }
}