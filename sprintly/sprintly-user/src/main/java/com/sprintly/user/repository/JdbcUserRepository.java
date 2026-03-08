package com.sprintly.user.repository;

import com.sprintly.common.enums.UserRole;
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
 * JDBC-based implementation of UserRepository.
 * Uses Spring's JdbcTemplate for direct SQL queries.
 */
@Component
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setOauth2Provider(rs.getString("oauth2_provider"));
        user.setOauth2ProviderId(rs.getString("oauth2_provider_id"));
        user.setEnabled(rs.getBoolean("enabled"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return user;
    };

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    @Override
    public Optional<User> findByOauth2ProviderAndOauth2ProviderId(String provider, String providerId) {
        String sql = "SELECT * FROM users WHERE oauth2_provider = ? AND oauth2_provider_id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, provider, providerId);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            // INSERT
            String sql = "INSERT INTO users (name, email, password, role, oauth2_provider, oauth2_provider_id, enabled, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?::user_role, ?, ?, ?, ?, ?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, user.getName());
                ps.setString(2, user.getEmail());
                ps.setString(3, user.getPassword());
                ps.setString(4, user.getRole().name());
                ps.setString(5, user.getOauth2Provider());
                ps.setString(6, user.getOauth2ProviderId());
                ps.setBoolean(7, user.isEnabled());
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                return ps;
            }, keyHolder);
            user.setId(keyHolder.getKey().longValue());
        } else {
            // UPDATE
            String sql = "UPDATE users SET name = ?, email = ?, password = ?, role = ?::user_role, oauth2_provider = ?, oauth2_provider_id = ?, enabled = ?, updated_at = ? WHERE id = ?";
            jdbcTemplate.update(sql, user.getName(), user.getEmail(), user.getPassword(), user.getRole().name(),
                    user.getOauth2Provider(), user.getOauth2ProviderId(), user.isEnabled(), Timestamp.valueOf(LocalDateTime.now()), user.getId());
        }
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
}
