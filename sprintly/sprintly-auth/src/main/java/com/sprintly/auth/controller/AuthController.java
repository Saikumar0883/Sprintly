package com.sprintly.auth.controller;

import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.auth.dto.RefreshTokenRequest;
import com.sprintly.auth.dto.RegisterRequest;
import com.sprintly.auth.service.AuthService;
import com.sprintly.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * ─────────────────────────────────────────────────────────────────
 *  AuthController
 * ─────────────────────────────────────────────────────────────────
 *  Exposes REST endpoints for user authentication.
 *
 *  Base path: /api/auth
 *
 *  All endpoints here are PUBLIC (no JWT required) except /logout
 *  which needs a valid token to identify the user being logged out.
 *
 *  @Valid on request bodies triggers Spring's Bean Validation.
 *  If validation fails, GlobalExceptionHandler returns 400 with
 *  per-field error details before this controller method is even called.
 *
 *  @AuthenticationPrincipal:
 *    Injects the UserDetails of the currently authenticated user
 *    directly from the SecurityContext — no manual token parsing needed.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
    name = "Authentication",
    description = "Register, login, refresh tokens and logout. All endpoints except /logout are public."
)
public class AuthController {

    private final AuthService authService;

    // ── Register ────────────────────────────────────────────────────

    @Operation(
        summary     = "Register a new user account",
        description = "Creates a new user with ROLE_DEVELOPER and returns JWT tokens. " +
                      "The user is immediately logged in after registration."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description  = "User registered successfully",
            content      = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description  = "Email already registered or validation failed"
        )
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        log.info("Register request for email: {}", request.getEmail());
        AuthResponse authResponse = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", authResponse));
    }

    // ── Login ───────────────────────────────────────────────────────

    @Operation(
        summary     = "Login with email and password",
        description = "Authenticates the user and returns an access token (15 min) " +
                      "and refresh token (7 days). Use the access token as: " +
                      "Authorization: Bearer <accessToken>"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description  = "Login successful",
            content      = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description  = "Invalid email or password"
        )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for email: {}", request.getEmail());
        AuthResponse authResponse = authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success("Login successful", authResponse)
        );
    }

    // ── Refresh Token ───────────────────────────────────────────────

    @Operation(
        summary     = "Refresh access token",
        description = "Exchanges a valid refresh token for a new access token. " +
                      "The old refresh token is invalidated (token rotation). " +
                      "Call this when the access token is about to expire."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description  = "New tokens issued",
            content      = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description  = "Refresh token is invalid, expired, or revoked"
        )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse authResponse = authService.refresh(request);

        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", authResponse)
        );
    }

    // ── Logout ──────────────────────────────────────────────────────

    @Operation(
        summary     = "Logout — revoke all sessions",
        description = "Invalidates all refresh tokens for the authenticated user. " +
                      "Effectively logs out from all devices. " +
                      "The current access token will expire naturally (15 min).",
        security    = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description  = "Logged out successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description  = "Not authenticated"
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails) {

        /*
         * @AuthenticationPrincipal injects the UserDetails that JwtAuthFilter
         * placed in the SecurityContext when it validated the Bearer token.
         * We extract the email (username) and pass it to AuthService.
         */
        authService.logout(userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully from all devices")
        );
    }

    // ── OAuth2 Info ─────────────────────────────────────────────────

    @Operation(
        summary     = "Google OAuth2 login entry point",
        description = "Redirects to Google's consent screen. " +
                      "Not called directly — click the link to initiate OAuth2 flow. " +
                      "On success, redirects to frontend with access + refresh tokens."
    )
    @GetMapping("/oauth2/info")
    public ResponseEntity<ApiResponse<String>> oauth2Info() {
        return ResponseEntity.ok(
                ApiResponse.success("OAuth2 ready",
                        "Navigate to: /oauth2/authorization/google to start Google login")
        );
    }
}
