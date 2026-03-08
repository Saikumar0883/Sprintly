package com.sprintly.auth.repository;

import com.sprintly.auth.entity.RefreshToken;
import com.sprintly.user.entity.User;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ─────────────────────────────────────────────────────────────────
 *  RefreshTokenRepository
 * ─────────────────────────────────────────────────────────────────
 *  Data access interface for refresh tokens.
 *
 *  Implementation uses Spring JDBC for persistence operations.
 * ─────────────────────────────────────────────────────────────────
 */
@Repository
public interface RefreshTokenRepository {

    /**
     * Find a specific refresh token by its string value.
     * Called on every POST /api/auth/refresh request.
     *
     * @param token the raw token string
     * @return Optional with the token entity, or empty if not found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Delete ALL refresh tokens for a specific user.
     * Called on logout to invalidate all sessions across all devices.
     *
     * @param user the user whose tokens should be purged
     */
    void deleteAllByUser(User user);

    /**
     * Scheduled cleanup: delete tokens that are expired OR revoked.
     * Run this via @Scheduled in a maintenance task to keep the table lean.
     *
     * @param now current timestamp — tokens with expiresAt before this are stale
     */
    void deleteExpiredAndRevoked(LocalDateTime now);

    /**
     * Save a new refresh token.
     *
     * @param token the refresh token entity to persist
     * @return the saved token with ID populated
     */
    RefreshToken save(RefreshToken token);

    /**
     * Find a refresh token by ID.
     *
     * @param id the refresh token ID
     * @return Optional containing the token, or empty if not found
     */
    Optional<RefreshToken> findById(Long id);

    /**
     * Delete a refresh token by ID.
     *
     * @param id the refresh token ID to delete
     * @return true if a token was deleted, false if not found
     */
    boolean deleteById(Long id);
}
