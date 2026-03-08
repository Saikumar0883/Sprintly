package com.sprintly.auth.entity;

import com.sprintly.user.entity.User;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ─────────────────────────────────────────────────────────────────
 *  RefreshToken Entity
 * ─────────────────────────────────────────────────────────────────
 *  Persists issued refresh tokens in the database.
 *
 *  Why store refresh tokens in DB?
 *    Unlike access tokens (stateless JWTs verified by signature),
 *    refresh tokens need to be revocable. Storing them in DB allows:
 *      1. Explicit logout   — delete the token record
 *      2. Token rotation    — invalidate old token on each use
 *      3. Suspicious activity — detect if a token is used twice
 *         (sign of token theft — rotate + alert)
 *
 *  Lifecycle:
 *    POST /api/auth/login   → creates RefreshToken record
 *    POST /api/auth/refresh → validates, deletes old, creates new
 *    POST /api/auth/logout  → deletes the record
 *
 *  revoked flag:
 *    Soft-invalidation. Allows keeping audit history while
 *    preventing further use. A scheduled job can hard-delete
 *    revoked/expired tokens periodically.
 * ─────────────────────────────────────────────────────────────────
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    private Long id;

    /**
     * The actual refresh token string (UUID or JWT).
     * Stored as a unique index for fast lookup during /refresh calls.
     */
    private String token;

    /**
     * The user this refresh token belongs to.
     * Many refresh tokens can exist per user (multiple devices/sessions).
     */
    private User user;

    /**
     * Absolute expiry timestamp.
     * Checked during /refresh to reject tokens even if they exist in DB.
     */
    private LocalDateTime expiresAt;

    /**
     * Soft-revocation flag.
     * Set to true on logout or when a used token is rotated.
     * Revoked tokens are rejected immediately without checking expiry.
     */
    @Builder.Default
    private boolean revoked = false;

    /** Timestamp when this token was first issued. */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Helper methods ─────────────────────────────────────────────

    /**
     * Checks if this token can still be used.
     * A token is valid only if: not revoked AND not past expiry date.
     *
     * @return true if the token is still usable
     */
    public boolean isValid() {
        return !revoked && LocalDateTime.now().isBefore(expiresAt);
    }

    /** Marks this token as revoked (used during logout or rotation). */
    public void revoke() {
        this.revoked = true;
    }
}
