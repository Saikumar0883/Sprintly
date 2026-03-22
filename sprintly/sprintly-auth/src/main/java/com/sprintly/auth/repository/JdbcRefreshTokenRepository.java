package com.sprintly.auth.repository;

import com.sprintly.auth.entity.RefreshToken;
import com.sprintly.user.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JDBC-based implementation of RefreshTokenRepository.
 *
 * ROOT CAUSE OF BUG:
 *   The original tokenRowMapper only read refresh_token columns.
 *   It never set token.setUser(...), so storedToken.getUser() returned null.
 *
 *   AuthService.refresh() then did:
 *     User user = storedToken.getUser();   // ← null
 *     user.getEmail()                       // ← NullPointerException!
 *
 * FIX:
 *   findByToken() now uses LEFT JOIN users ON refresh_tokens.user_id = users.id
 *   The rowMapper reads user columns (u.id, u.name, u.email, u.password, u.enabled)
 *   and builds a User object, then sets it on the RefreshToken.
 *
 *   This ensures storedToken.getUser() is ALWAYS non-null when the token exists.
 */
@Component
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * RowMapper that reads refresh token + joined user columns.
     *
     * SQL alias prefix "u." is used for user columns to avoid collision
     * with refresh_token columns that share names (e.g. "id", "created_at").
     */
    private final RowMapper<RefreshToken> tokenWithUserRowMapper = (rs, rowNum) -> {
        // ── Refresh token fields ──────────────────────────────────────────────
        RefreshToken token = new RefreshToken();
        token.setId(rs.getLong("rt_id"));
        token.setToken(rs.getString("token"));
        token.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        token.setRevoked(rs.getBoolean("revoked"));
        token.setCreatedAt(rs.getTimestamp("rt_created_at").toLocalDateTime());

        // ── User fields (from JOIN) ───────────────────────────────────────────
        // This is the fix: populate the User object so storedToken.getUser()
        // never returns null in AuthService.refresh()
        User user = new User();
        user.setId(rs.getLong("u_id"));
        user.setName(rs.getString("u_name"));
        user.setEmail(rs.getString("u_email"));
        user.setPassword(rs.getString("u_password"));
        user.setEnabled(rs.getBoolean("u_enabled"));

        Timestamp userCreatedAt = rs.getTimestamp("u_created_at");
        if (userCreatedAt != null) {
            user.setCreatedAt(userCreatedAt.toLocalDateTime());
        }

        token.setUser(user);
        return token;
    };

    /**
     * Simple rowMapper for operations that don't need the User object
     * (e.g. save/update where we already have the user).
     */
    private final RowMapper<RefreshToken> tokenOnlyRowMapper = (rs, rowNum) -> {
        RefreshToken token = new RefreshToken();
        token.setId(rs.getLong("id"));
        token.setToken(rs.getString("token"));
        token.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        token.setRevoked(rs.getBoolean("revoked"));
        token.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return token;
    };

    /**
     * Find a refresh token by its string value.
     *
     * Uses LEFT JOIN to load the associated User in the same query.
     * This prevents NullPointerException in AuthService.refresh() where
     * storedToken.getUser() is called immediately after.
     *
     * Column aliases used to avoid name collisions:
     *   rt.id        AS rt_id
     *   rt.created_at AS rt_created_at
     *   u.id         AS u_id
     *   u.name       AS u_name
     *   etc.
     */
    @Override
    public Optional<RefreshToken> findByToken(String token) {
        String sql = """
                SELECT
                    rt.id          AS rt_id,
                    rt.token,
                    rt.expires_at,
                    rt.revoked,
                    rt.created_at  AS rt_created_at,
                    u.id           AS u_id,
                    u.name         AS u_name,
                    u.email        AS u_email,
                    u.password     AS u_password,
                    u.enabled      AS u_enabled,
                    u.created_at   AS u_created_at
                FROM refresh_tokens rt
                LEFT JOIN users u ON rt.user_id = u.id
                WHERE rt.token = ?
                """;
        try {
            RefreshToken refreshToken = jdbcTemplate.queryForObject(sql, tokenWithUserRowMapper, token);
            return Optional.ofNullable(refreshToken);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        if (token.getId() == null) {
            // INSERT
            String sql = """
                    INSERT INTO refresh_tokens (token, user_id, expires_at, revoked, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, token.getToken());
                ps.setLong(2, token.getUser().getId());
                ps.setTimestamp(3, Timestamp.valueOf(token.getExpiresAt()));
                ps.setBoolean(4, token.isRevoked());
                ps.setTimestamp(5, Timestamp.valueOf(
                        token.getCreatedAt() != null ? token.getCreatedAt() : LocalDateTime.now()));
                return ps;
            }, keyHolder);
            token.setId(keyHolder.getKey().longValue());
        } else {
            // UPDATE — only revoked + expires_at can change (rotation)
            String sql = """
                    UPDATE refresh_tokens
                    SET token = ?, expires_at = ?, revoked = ?
                    WHERE id = ?
                    """;
            jdbcTemplate.update(sql,
                    token.getToken(),
                    Timestamp.valueOf(token.getExpiresAt()),
                    token.isRevoked(),
                    token.getId());
        }
        return token;
    }

    @Override
    public void deleteAllByUser(User user) {
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", user.getId());
    }

    @Override
    public void deleteExpiredAndRevoked(LocalDateTime now) {
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE expires_at < ? OR revoked = true",
                Timestamp.valueOf(now));
    }

    @Override
    public Optional<RefreshToken> findById(Long id) {
        // Simple lookup — user not needed here
        String sql = "SELECT * FROM refresh_tokens WHERE id = ?";
        try {
            RefreshToken token = jdbcTemplate.queryForObject(sql, tokenOnlyRowMapper, id);
            return Optional.ofNullable(token);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteById(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM refresh_tokens WHERE id = ?", id);
        return rows > 0;
    }
}