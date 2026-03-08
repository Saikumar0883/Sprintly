package com.sprintly.auth.security;

import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.entity.RefreshToken;
import com.sprintly.auth.repository.RefreshTokenRepository;
import com.sprintly.auth.service.JwtService;
import com.sprintly.common.enums.UserRole;
import com.sprintly.user.entity.User;
import com.sprintly.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * ─────────────────────────────────────────────────────────────────
 *  OAuth2SuccessHandler
 * ─────────────────────────────────────────────────────────────────
 *  Called by Spring Security after a successful Google OAuth2 login.
 *
 *  Flow:
 *    1. User clicks "Login with Google"
 *    2. Google redirects back with an authorization code
 *    3. Spring Security exchanges it for user profile attributes
 *    4. This handler fires with the authenticated OAuth2User
 *    5. We find-or-create a local User record
 *    6. Issue JWT tokens (same as email/password login)
 *    7. Redirect the browser to the frontend with tokens as query params
 *       (in production: use httpOnly cookies or a secure token exchange)
 *
 *  Find-or-create pattern:
 *    First OAuth2 login  → creates a new User (no password)
 *    Subsequent logins   → finds the existing User by provider + providerId
 *    This means Google users don't need to register first.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService             jwtService;

    /** Frontend URL to redirect to after OAuth2 login with tokens */
    private static final String FRONTEND_REDIRECT = "http://localhost:3000/oauth2/callback";

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        Authentication      authentication) throws IOException {

        // OAuth2User contains attributes from Google's userinfo endpoint
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // Extract profile fields from Google's attribute map
        String email      = oauth2User.getAttribute("email");
        String name       = oauth2User.getAttribute("name");
        String providerId = oauth2User.getAttribute("sub");   // Google's unique user ID

        log.info("OAuth2 login: email={}, provider=google, providerId={}", email, providerId);

        // ── Find existing user or create a new one ─────────────────────
        User user = userRepository
                .findByOauth2ProviderAndOauth2ProviderId("google", providerId)
                .orElseGet(() -> {
                    // First-time Google login — register a new User
                    log.info("First OAuth2 login, creating user: {}", email);
                    return userRepository.save(
                            User.builder()
                                    .name(name)
                                    .email(email)
                                    .password(null)             // no password for OAuth2 users
                                    .role(UserRole.ROLE_DEVELOPER)
                                    .oauth2Provider("google")
                                    .oauth2ProviderId(providerId)
                                    .enabled(true)
                                    .build()
                    );
                });

        // ── Generate tokens ────────────────────────────────────────────
        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password("")
                        .authorities(user.getRole().name())
                        .build();

        String accessToken  = jwtService.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // Persist refresh token
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiresAt(LocalDateTime.now().plusSeconds(
                                jwtService.getRefreshTokenExpiryMs() / 1000))
                        .build()
        );

        /*
         * Redirect to frontend with tokens as query parameters.
         *
         * NOTE: In a production application, avoid passing tokens in URLs
         * (they appear in browser history and server logs).
         * Prefer httpOnly cookies or a short-lived authorization code
         * that the frontend exchanges for tokens via a separate API call.
         */
        String redirectUrl = FRONTEND_REDIRECT
                + "?accessToken="  + accessToken
                + "&refreshToken=" + refreshToken
                + "&userId="       + user.getId();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
