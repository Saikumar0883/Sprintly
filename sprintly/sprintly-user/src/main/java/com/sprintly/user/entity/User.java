package com.sprintly.user.entity;

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
 *    - password is required since all users register with one
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
     */
    private String name;

    /**
     * Unique email — used as the primary login identifier.
     */
    private String email;

    /**
     * BCrypt-hashed password.
     */
    private String password;


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
