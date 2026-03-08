package com.sprintly.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ─────────────────────────────────────────────────────────────────
 *  RefreshTokenRequest DTO
 * ─────────────────────────────────────────────────────────────────
 *  Inbound payload for POST /api/auth/refresh.
 *
 *  The client sends the refresh token it stored after login.
 *  AuthService validates it against the DB record and if valid,
 *  issues a new access token (and optionally a new refresh token
 *  for sliding-window expiry).
 * ─────────────────────────────────────────────────────────────────
 */
@Data
@Schema(description = "Request payload for refreshing an access token")
public class RefreshTokenRequest {

    @Schema(
        description = "The refresh token received during login",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123"
    )
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
