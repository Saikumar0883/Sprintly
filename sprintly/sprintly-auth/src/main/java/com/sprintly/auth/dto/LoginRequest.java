package com.sprintly.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ─────────────────────────────────────────────────────────────────
 *  LoginRequest DTO
 * ─────────────────────────────────────────────────────────────────
 *  Inbound payload for POST /api/auth/login.
 *
 *  On successful authentication, AuthService issues:
 *    - Short-lived access token  (15 min) → used for API calls
 *    - Long-lived refresh token  (7 days) → used to get new access tokens
 * ─────────────────────────────────────────────────────────────────
 */
@Data
@Schema(description = "Request payload for user login")
public class LoginRequest {

    @Schema(description = "Registered email address", example = "ravi@sprintly.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Schema(description = "Account password", example = "MyPass@123")
    @NotBlank(message = "Password is required")
    private String password;
}
