package com.sprintly.auth.security;

import com.sprintly.auth.service.CustomUserDetailsService;
import com.sprintly.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ─────────────────────────────────────────────────────────────────
 *  JwtAuthFilter
 * ─────────────────────────────────────────────────────────────────
 *  Intercepts every HTTP request and validates the JWT Bearer token.
 *
 *  Extends OncePerRequestFilter to guarantee exactly one execution
 *  per request, even in async dispatch scenarios.
 *
 *  Request flow:
 *    1. Extract "Authorization: Bearer <token>" header
 *    2. If no header → skip filter (public route, handled by SecurityConfig)
 *    3. Validate token structure (signature + expiry)
 *    4. Extract subject (email) from token
 *    5. Load UserDetails from DB (to get authorities/role)
 *    6. Validate token against UserDetails
 *    7. Populate SecurityContextHolder → request is authenticated
 *
 *  Why load UserDetails from DB even if the token has the role?
 *    - The DB is the source of truth for account status (enabled/locked)
 *    - If an admin disables an account, the JWT would still pass
 *      signature+expiry checks. Loading from DB catches this case.
 *    - Trade-off: one extra DB call per request (acceptable performance overhead
 *      in a production setup)
 *
 *  SecurityContextHolder:
 *    Once set, all downstream components (controllers, services with
 *    @PreAuthorize) can call SecurityContextHolder.getContext()
 *    .getAuthentication() to access the current user.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";

    private final JwtService               jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {

        // ── Step 1: Extract the Authorization header ─────────────────
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        /*
         * If no Authorization header or it doesn't start with "Bearer ",
         * this is either a public route or a request missing credentials.
         * Pass it along — SecurityConfig will reject it if auth is required.
         */
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract the raw token string ──────────────────────
        final String token = authHeader.substring(BEARER_PREFIX.length());

        // ── Step 3: Basic token structure validation ──────────────────
        if (!jwtService.isTokenStructureValid(token)) {
            log.debug("Invalid JWT structure for request: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 4: Extract the subject (email) ───────────────────────
        final String userEmail = jwtService.extractSubject(token);

        /*
         * Only authenticate if:
         *   - subject was extracted successfully (not null)
         *   - no authentication is already set (avoid double-processing)
         */
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Step 5: Load UserDetails from DB ─────────────────────
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // ── Step 6: Validate token against UserDetails ────────────
            if (jwtService.isTokenValid(token, userDetails)) {

                /*
                 * Create an authenticated token and set it in the SecurityContext.
                 *
                 * UsernamePasswordAuthenticationToken(principal, credentials, authorities):
                 *   - principal   : UserDetails (email + role)
                 *   - credentials : null (we don't store credentials post-auth)
                 *   - authorities : user's GrantedAuthority list (e.g. ROLE_DEVELOPER)
                 *
                 * setDetails: attaches request metadata (IP, session) to the token
                 * for use in audit logging or security events.
                 */
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ── Step 7: Authenticate the request ─────────────────
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user: {} for {} {}",
                        userEmail, request.getMethod(), request.getRequestURI());
            }
        }

        // Continue the filter chain — the request is now authenticated (or not, if invalid)
        filterChain.doFilter(request, response);
    }

    /**
     * Skip this filter for public auth endpoints.
     * These routes don't have a JWT yet (they're how you get one).
     * Without this, login/register would try to validate a non-existent token.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/oauth2/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
