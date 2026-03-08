package com.sprintly.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ─────────────────────────────────────────────────────────────────
 *  RegisterRequest DTO
 * ─────────────────────────────────────────────────────────────────
 *  Inbound payload for POST /api/auth/register.
 *
 *  Validation annotations (@NotBlank, @Email, @Size) are processed
 *  by Spring's @Valid in AuthController before the service is called.
 *  If validation fails, GlobalExceptionHandler returns a 400 with
 *  field-level error messages.
 *
 *  @Schema annotations populate the Swagger UI with example values
 *  so API testers know exactly what to send.
 * ─────────────────────────────────────────────────────────────────
 */
@Data
@Schema(description = "Request payload for registering a new user account")
public class RegisterRequest {

    @Schema(description = "Full name of the user", example = "Ravi Kumar")
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Schema(description = "Email address used for login", example = "ravi@sprintly.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @Schema(description = "Password (min 8 characters)", example = "MyPass@123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}
