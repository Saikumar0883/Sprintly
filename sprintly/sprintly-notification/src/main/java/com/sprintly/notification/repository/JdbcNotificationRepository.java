package com.sprintly.notification.repository;

import com.sprintly.notification.entity.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Notification> notificationRowMapper = new NotificationRowMapper();

    @Override
    public Notification save(Notification notification) {
        if (notification.getId() == null) {
            return insert(notification);
        } else {
            return update(notification);
        }
    }

    private Notification insert(Notification notification) {
        String sql = """
            INSERT INTO notifications (type, title, message, recipient_id, sender_id,
                                     entity_id, entity_type, read, created_at, read_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, notification.getType());
            ps.setString(2, notification.getTitle());
            ps.setString(3, notification.getMessage());
            ps.setLong(4, notification.getRecipientId());
            if (notification.getSenderId() != null) {
                ps.setLong(5, notification.getSenderId());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            if (notification.getEntityId() != null) {
                ps.setLong(6, notification.getEntityId());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setString(7, notification.getEntityType());
            ps.setBoolean(8, notification.isRead());
            ps.setObject(9, notification.getCreatedAt());
            ps.setObject(10, notification.getReadAt());
            return ps;
        }, keyHolder);

        Long generatedId = keyHolder.getKey().longValue();
        notification.setId(generatedId);
        return notification;
    }

    private Notification update(Notification notification) {
        String sql = """
            UPDATE notifications SET type = ?, title = ?, message = ?, recipient_id = ?,
                                   sender_id = ?, entity_id = ?, entity_type = ?, read = ?,
                                   created_at = ?, read_at = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getRecipientId(),
            notification.getSenderId(),
            notification.getEntityId(),
            notification.getEntityType(),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getReadAt(),
            notification.getId()
        );

        return notification;
    }

    @Override
    public Optional<Notification> findById(Long id) {
        String sql = "SELECT * FROM notifications WHERE id = ?";
        List<Notification> results = jdbcTemplate.query(sql, notificationRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Notification> findByRecipientId(Long recipientId) {
        String sql = "SELECT * FROM notifications WHERE recipient_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, notificationRowMapper, recipientId);
    }

    @Override
    public List<Notification> findUnreadByRecipientId(Long recipientId) {
        String sql = "SELECT * FROM notifications WHERE recipient_id = ? AND read = false ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, notificationRowMapper, recipientId);
    }

    @Override
    public boolean markAsRead(Long notificationId) {
        String sql = "UPDATE notifications SET read = true, read_at = ? WHERE id = ?";
        int updated = jdbcTemplate.update(sql, LocalDateTime.now(), notificationId);
        return updated > 0;
    }

    @Override
    public int markAllAsRead(Long recipientId) {
        String sql = "UPDATE notifications SET read = true, read_at = ? WHERE recipient_id = ? AND read = false";
        return jdbcTemplate.update(sql, LocalDateTime.now(), recipientId);
    }

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM notifications WHERE id = ?";
        int deleted = jdbcTemplate.update(sql, id);
        return deleted > 0;
    }

    @Override
    public int deleteByRecipientId(Long recipientId) {
        String sql = "DELETE FROM notifications WHERE recipient_id = ?";
        return jdbcTemplate.update(sql, recipientId);
    }

    @Override
    public int countUnreadByRecipientId(Long recipientId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND read = false";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, recipientId);
        return count != null ? count : 0;
    }

    private static class NotificationRowMapper implements RowMapper<Notification> {
        @Override
        public Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Notification.builder()
                .id(rs.getLong("id"))
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .message(rs.getString("message"))
                .recipientId(rs.getLong("recipient_id"))
                .senderId(rs.getObject("sender_id", Long.class))
                .entityId(rs.getObject("entity_id", Long.class))
                .entityType(rs.getString("entity_type"))
                .read(rs.getBoolean("read"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .readAt(rs.getObject("read_at", LocalDateTime.class))
                .build();
        }
    }
}