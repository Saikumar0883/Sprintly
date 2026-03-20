package com.sprintly.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ─────────────────────────────────────────────────────────────────
 *  AuthResponse DTO
 * ─────────────────────────────────────────────────────────────────
 *  Outbound payload returned after successful login or token refresh.
 *
 *  Design Pattern: Builder
 *    Clean construction without telescoping constructors.
 *
 *  Fields:
 *    accessToken   — short-lived JWT for authenticating API requests
 *                    (sent as Authorization: Bearer <token>)
 *    refreshToken  — long-lived token for getting new access tokens
 *                    (store securely on client; use POST /api/auth/refresh)
 *    tokenType     — always "Bearer" per OAuth2 / RFC 6750 convention
 *    expiresIn     — access token lifetime in seconds (helps clients
 *                    schedule proactive refresh before expiry)
 *    userId, email — convenience fields so the client doesn't need
 *                    to decode the JWT to know who just logged in
 *
 *  @JsonInclude(NON_NULL): refreshToken is omitted on /refresh responses
 *  that use token rotation (only new accessToken is returned).
 * ─────────────────────────────────────────────────────────────────
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response returned on successful authentication")
public class AuthResponse {

    @Schema(description = "JWT access token for API authorization", example = "eyJhbGci...")
    private String accessToken;

    @Schema(description = "Refresh token for obtaining new access tokens", example = "eyJhbGci...")
    private String refreshToken;

    @Schema(description = "Token type, always Bearer", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifetime in seconds", example = "900")
    private long expiresIn;

    @Schema(description = "ID of the authenticated user", example = "1")
    private Long userId;

    @Schema(description = "Email of the authenticated user", example = "ravi@sprintly.com")
    private String email;
}
