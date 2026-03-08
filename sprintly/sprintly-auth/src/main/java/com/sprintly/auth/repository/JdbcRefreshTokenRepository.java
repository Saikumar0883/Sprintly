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
 * Uses Spring's JdbcTemplate for direct SQL queries.
 */
@Component
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RefreshToken> tokenRowMapper = (rs, rowNum) -> {
        RefreshToken token = new RefreshToken();
        token.setId(rs.getLong("id"));
        token.setToken(rs.getString("token"));
        token.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        token.setRevoked(rs.getBoolean("revoked"));
        token.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        // Note: user_id is stored in DB but we don't load full User object here
        // If needed, query separately or use a JOIN
        return token;
    };

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        String sql = "SELECT * FROM refresh_tokens WHERE token = ?";
        try {
            RefreshToken refreshToken = jdbcTemplate.queryForObject(sql, tokenRowMapper, token);
            return Optional.ofNullable(refreshToken);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteAllByUser(User user) {
        String sql = "DELETE FROM refresh_tokens WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    @Override
    public void deleteExpiredAndRevoked(LocalDateTime now) {
        String sql = "DELETE FROM refresh_tokens WHERE expires_at < ? OR revoked = true";
        jdbcTemplate.update(sql, Timestamp.valueOf(now));
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        if (token.getId() == null) {
            // INSERT
            String sql = "INSERT INTO refresh_tokens (token, user_id, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, token.getToken());
                ps.setLong(2, token.getUser().getId());
                ps.setTimestamp(3, Timestamp.valueOf(token.getExpiresAt()));
                ps.setBoolean(4, token.isRevoked());
                ps.setTimestamp(5, Timestamp.valueOf(token.getCreatedAt() != null ? token.getCreatedAt() : LocalDateTime.now()));
                return ps;
            }, keyHolder);
            token.setId(keyHolder.getKey().longValue());
        } else {
            // UPDATE
            String sql = "UPDATE refresh_tokens SET token = ?, expires_at = ?, revoked = ? WHERE id = ?";
            jdbcTemplate.update(sql, token.getToken(), Timestamp.valueOf(token.getExpiresAt()), token.isRevoked(), token.getId());
        }
        return token;
    }

    @Override
    public Optional<RefreshToken> findById(Long id) {
        String sql = "SELECT * FROM refresh_tokens WHERE id = ?";
        try {
            RefreshToken token = jdbcTemplate.queryForObject(sql, tokenRowMapper, id);
            return Optional.ofNullable(token);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM refresh_tokens WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
}
