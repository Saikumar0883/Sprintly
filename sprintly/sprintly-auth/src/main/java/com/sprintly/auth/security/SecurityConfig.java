package com.sprintly.auth.security;

import com.sprintly.auth.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ─────────────────────────────────────────────────────────────────
 * SecurityConfig
 * ─────────────────────────────────────────────────────────────────
 * Central Spring Security configuration for the entire application.
 *
 * Key decisions:
 *
 * 1. STATELESS sessions (SessionCreationPolicy.STATELESS)
 * No HttpSession is created. Each request must carry its own JWT.
 * This makes the API horizontally scalable — any server instance
 * can handle any request without shared session state.
 *
 * 2. CSRF disabled
 * CSRF protection is for browser cookie-based sessions.
 * Since we use JWT in the Authorization header (not cookies),
 * CSRF attacks are not applicable here.
 *
 * 3. JwtAuthFilter before UsernamePasswordAuthenticationFilter
 * Our filter runs first and populates the SecurityContext if
 * the JWT is valid. Spring's default filter then finds the
 * context already set and skips credential re-processing.
 *
 * 4. @EnableMethodSecurity
 * Activates @PreAuthorize on controller/service methods.
 * Example: @PreAuthorize("hasRole('MANAGER')") on delete endpoints.
 *
 * 5. DaoAuthenticationProvider
 * Wires our CustomUserDetailsService + BCryptPasswordEncoder so
 * Spring knows HOW to load and verify user credentials.
 *
 * Public routes (no JWT required):
 * POST /api/auth/** → register, login, refresh
 * GET /swagger-ui/** → API documentation
 * GET /v3/api-docs/** → OpenAPI spec JSON
 * GET /actuator/health → health check
 * GET /oauth2/** → Google OAuth2 redirect
 * ─────────────────────────────────────────────────────────────────
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize, @PostAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Main security filter chain.
     * Defines which routes are public and which require authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── Disable CSRF (JWT-based API, not cookie-session) ───────
                .csrf(AbstractHttpConfigurer::disable)

                // ── Route-level authorization rules ───────────────────────
                .authorizeHttpRequests(auth -> auth

                        // Auth endpoints — public (no token needed)
                        .requestMatchers("/api/auth/**").permitAll()

                        // Swagger UI — allow during development
                        // Note: some swagger installations use /swagger/** (e.g. /swagger/index.html)
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/swagger/**",
                                "/v3/api-docs/**", "/v3/api-docs",
                                "/swagger-ui/oauth2-redirect.html")
                        .permitAll()

                        // Actuator health — allow for load balancer checks
                        .requestMatchers("/actuator/health").permitAll()

                        // User management — authenticated users only
                        // Fine-grained role checks done via @PreAuthorize in controllers
                        .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").authenticated()

                        // Task routes — authenticated, role checked at method level
                        .requestMatchers("/api/tasks/**").authenticated()

                        // Notification/WebSocket routes
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/ws/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated())

                // ── Session management: stateless (no HttpSession) ────────
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Wire our DaoAuthenticationProvider ────────────────────
                .authenticationProvider(authenticationProvider())

                // ── Register JwtAuthFilter BEFORE Spring's default filter ─
                // Our filter validates the JWT and sets the SecurityContext.
                // Spring's UsernamePasswordAuthenticationFilter then sees
                // the context already populated and does not re-authenticate.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder — industry standard for password hashing.
     *
     * Strength 12 (default is 10): ~0.5 second hash time.
     * Higher = more secure against brute-force, but slower on login.
     * Strength 12 is a good balance for a typical web application.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * DaoAuthenticationProvider: wires together how Spring Security
     * loads users (our service) and how it checks passwords (BCrypt).
     *
     * This bean is used by AuthenticationManager (→ used in AuthService.login).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager bean — exposes Spring's authentication mechanism.
     * Injected into AuthService so we can call authenticate() manually
     * during login without going through an HTTP form.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
