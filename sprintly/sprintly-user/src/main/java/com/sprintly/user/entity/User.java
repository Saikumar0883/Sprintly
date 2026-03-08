package com.sprintly.user.entity;

import com.sprintly.common.enums.UserRole;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ─────────────────────────────────────────────────────────────────
 *  User Entity
 * ─────────────────────────────────────────────────────────────────
 *  Represents an application user stored in the `users` table.
 *
 *  This entity is the central identity object referenced by:
 *    - taskflow-auth  → login, token generation, OAuth2 mapping
 *    - taskflow-user  → profile management, role assignment
 *    - taskflow-task  → createdBy, assignedTo relationships
 *
 *  Design notes:
 *    - password is nullable to support OAuth2-only users (Google login)
 *      who never set a local password
 *    - role uses String so DB stores readable values
 *      like "ROLE_ADMIN" instead of fragile numeric values
 *    - enabled flag allows soft-disabling users without deleting records
 * ─────────────────────────────────────────────────────────────────
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private Long id;

    /**
     * Full display name of the user.
     * For OAuth2 users, this is populated from the provider's profile.
     */
    private String name;

    /**
     * Unique email — used as the primary login identifier.
     * Also sourced from OAuth2 provider on first login.
     */
    private String email;

    /**
     * BCrypt-hashed password.
     * NULL for users who signed up via Google OAuth2 (they never set a password).
     */
    private String password;

    /**
     * Application role controlling endpoint access via @PreAuthorize.
     * Default: ROLE_DEVELOPER — the least privileged role.
     */
    @Builder.Default
    private UserRole role = UserRole.ROLE_DEVELOPER;

    /**
     * OAuth2 provider name, e.g. "google", "github".
     * NULL for users who registered with email + password.
     */
    private String oauth2Provider;

    /**
     * Unique ID from the OAuth2 provider (subject claim).
     * Stored so we can find the user on subsequent OAuth2 logins
     * without re-fetching by email (which could theoretically change).
     */
    private String oauth2ProviderId;

    /**
     * Whether the account is active.
     * Disabled users receive 401 on login attempts.
     * Allows admins to suspend users without deletion.
     */
    @Builder.Default
    private boolean enabled = true;

    /** Auto-set on INSERT — never updated after creation. */
    private LocalDateTime createdAt;

    /** Auto-set on every UPDATE — tracks last profile change. */
    private LocalDateTime updatedAt;
}
