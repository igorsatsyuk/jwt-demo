package lt.satsyuk.config;

import lt.satsyuk.auth.JsonAccessDeniedHandler;
import lt.satsyuk.auth.JsonAuthEntryPoint;
import lt.satsyuk.security.DpopAuthenticationFilter;
import lt.satsyuk.security.KeycloakOpaqueRoleConverter;
import lt.satsyuk.security.KeycloakOpaqueTokenIntrospector;
import lt.satsyuk.security.RateLimitingFilter;
import lt.satsyuk.security.TraceIdResponseHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({RateLimitProperties.class, DpopProperties.class})
public class SecurityConfig {

    @Bean
    @SuppressWarnings("java:S4502") // Stateless REST API uses Bearer JWT; no cookies, so CSRF is not applicable.
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OpaqueTokenIntrospector opaqueTokenIntrospector,
                                                   JsonAuthEntryPoint jsonAuthEntryPoint,
                                                   JsonAccessDeniedHandler jsonAccessDeniedHandler,
                                                   TraceIdResponseHeaderFilter traceIdResponseHeaderFilter,
                                                   DpopAuthenticationFilter dpopAuthenticationFilter,
                                                   RateLimitingFilter rateLimitingFilter) {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector))
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler)
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler)
                );

        http.addFilterBefore(traceIdResponseHeaderFilter, AuthenticationFilter.class);
        http.addFilterAfter(dpopAuthenticationFilter, AuthenticationFilter.class);
        http.addFilterAfter(rateLimitingFilter, DpopAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector(KeycloakProperties props,
                                                           KeycloakOpaqueRoleConverter roleConverter) {
        return new KeycloakOpaqueTokenIntrospector(
                props.getIntrospectionUrl(),
                props.getResourceClientId(),
                props.getResourceClientSecret(),
                roleConverter
        );
    }

}
