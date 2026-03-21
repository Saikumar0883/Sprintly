package com.sprintly.auth.service;

import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.auth.dto.RefreshTokenRequest;
import com.sprintly.auth.dto.RegisterRequest;
import com.sprintly.auth.entity.RefreshToken;
import com.sprintly.auth.repository.RefreshTokenRepository;
import com.sprintly.common.exception.BadRequestException;
import com.sprintly.common.exception.ResourceNotFoundException;
import com.sprintly.common.exception.UnauthorizedException;
import com.sprintly.user.entity.User;
import com.sprintly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ─────────────────────────────────────────────────────────────────
 *  AuthService
 * ─────────────────────────────────────────────────────────────────
 *  Core authentication business logic.
 *
 *  Responsibilities:
 *    register()  → validate → hash password → save user → issue tokens
 *    login()     → authenticate via Spring Security → issue tokens
 *    refresh()   → validate refresh token → rotate → issue new access token
 *    logout()    → revoke all refresh tokens for the user
 *
 *  Token rotation on refresh:
 *    Every /refresh call deletes the old refresh token and issues a new one.
 *    This "sliding window" approach limits the damage of a stolen refresh token:
 *    if an attacker uses it first, the legitimate user's next call will fail,
 *    alerting them that something is wrong.
 *
 *  @Transactional:
 *    DB writes (user save + token save) are wrapped in a transaction.
 *    If the token insert fails after the user insert, both roll back —
 *    preventing ghost users with no tokens.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository            userRepository;
    private final RefreshTokenRepository    refreshTokenRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JwtService                jwtService;
    private final AuthenticationManager     authenticationManager;

    // ── Register ────────────────────────────────────────────────────

    /**
     * Registers a new user with email + password.
     *
     * Steps:
     *   1. Check email uniqueness — throw 400 if already taken
     *   2. Hash the password with BCrypt (never store plaintext)
     *   3. Persist the new User entity
     *   4. Issue access + refresh tokens so the user is immediately logged in
     *
     * @param request validated registration payload
     * @return AuthResponse containing access token, refresh token and user info
     * @throws BadRequestException if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Step 1: ensure the email is not already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with email '" + request.getEmail() + "' already exists");
        }

        // Step 2: build and save the user with a hashed password
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} (id={})", savedUser.getEmail(), savedUser.getId());

        // Step 3: issue tokens and return
        return issueTokens(savedUser);
    }

    // ── Login ───────────────────────────────────────────────────────

    /**
     * Authenticates a user with email and password.
     *
     * Delegates to Spring Security's AuthenticationManager which:
     *   1. Loads UserDetails via CustomUserDetailsService
     *   2. Verifies the password using BCryptPasswordEncoder
     *   3. Checks that the account is enabled
     *
     * On success, issues a fresh access token + refresh token pair.
     *
     * @param request validated login payload
     * @return AuthResponse with tokens and user info
     * @throws UnauthorizedException if credentials are incorrect
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // Spring Security verifies credentials; throws BadCredentialsException on failure
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            // Map Spring Security exception to our own for consistent error responses
            throw new UnauthorizedException("Invalid email or password");
        }

        // Credentials verified — load the full user entity
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", null));

        log.info("User logged in: {} (id={})", user.getEmail(), user.getId());

        return issueTokens(user);
    }

    // ── Refresh ─────────────────────────────────────────────────────

    /**
     * Issues a new access token using a valid refresh token.
     * Implements token rotation: the old refresh token is revoked and
     * a new one is issued, extending the session sliding window.
     *
     * @param request contains the refresh token string
     * @return AuthResponse with new access token (and new refresh token)
     * @throws UnauthorizedException if the token is invalid, expired or revoked
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        // Find the token record in DB
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found — please login again"));

        // Validate: not revoked AND not expired
        if (!storedToken.isValid()) {
            throw new UnauthorizedException("Refresh token has expired or been revoked — please login again");
        }

        User user = storedToken.getUser();

        // Rotate: revoke the used token before issuing a new one
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        log.info("Refresh token rotated for user: {} (id={})", user.getEmail(), user.getId());

        return issueTokens(user);
    }

    // ── Logout ──────────────────────────────────────────────────────

    /**
     * Logs out the user by revoking all their refresh tokens.
     * This invalidates all sessions across all devices simultaneously.
     *
     * The current access token is short-lived (15 min) so it will
     * naturally expire. For instant access token invalidation, add
     * the token is persisted in the database for revocation tracking.
     *
     * @param userEmail email of the authenticated user (from JWT subject)
     */
    @Transactional
    public void logout(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Delete all refresh token records for this user
        refreshTokenRepository.deleteAllByUser(user);

        log.info("User logged out, all tokens revoked: {} (id={})", user.getEmail(), user.getId());
    }

    // ── Private Helpers ─────────────────────────────────────────────

    /**
     * Creates a UserDetails wrapper, generates both tokens,
     * persists the refresh token, and assembles the AuthResponse.
     *
     * Extracted as a private method because register(), login() and
     * refresh() all follow the exact same token issuance flow.
     *
     * @param user the authenticated/registered user entity
     * @return fully populated AuthResponse
     */
    private AuthResponse issueTokens(User user) {
        // Wrap user as Spring Security UserDetails for JwtService
        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword() != null ? user.getPassword() : "")
                        .authorities(java.util.List.of())
                        .build();

        // Generate access token (short-lived, carries claims)
        String accessToken = jwtService.generateAccessToken(
                userDetails, user.getId()
        );

        // Generate refresh token (long-lived, minimal claims)
        String refreshTokenStr = jwtService.generateRefreshToken(userDetails);

        // Persist the refresh token so it can be validated and revoked later
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtService.getRefreshTokenExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        // Build and return the response (access token expiry in seconds)
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
