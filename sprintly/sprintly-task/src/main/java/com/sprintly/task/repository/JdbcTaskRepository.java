package com.sprintly.task.repository;

import com.sprintly.task.entity.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * RowMapper with TWO LEFT JOINs on users:
     *   assignee → gets assigneeName
     *   reporter → gets reporterName (same as createdByName)
     *
     * Both JOINs are LEFT JOINs so:
     *   - Unassigned tasks still appear (assignee JOIN returns null)
     *   - Tasks where creator was deleted still appear (reporter JOIN returns null)
     */
    private final RowMapper<Task> rowMapper = (rs, rowNum) -> {
        Task t = new Task();
        t.setId(rs.getLong("id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setStatus(rs.getString("status"));

        long createdBy = rs.getLong("created_by");
        t.setCreatedBy(createdBy);
        t.setReporterId(createdBy);   // reporter = creator

        // Handle nullable assigned_to
        long assignedTo = rs.getLong("assigned_to");
        t.setAssignedTo(rs.wasNull() ? null : assignedTo);

        // Names from JOINs
        t.setAssigneeName(rs.getString("assignee_name"));
        t.setReporterName(rs.getString("reporter_name"));
        t.setCreatedByName(rs.getString("reporter_name")); // keep legacy field in sync

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) t.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) t.setUpdatedAt(updatedAt.toLocalDateTime());

        return t;
    };

    /**
     * Base SELECT with two LEFT JOINs:
     *   assignee: gets the name of whoever the task is assigned to
     *   reporter: gets the name of whoever created the task
     *
     * Using table aliases to avoid column name conflicts.
     */
    private static final String SELECT_WITH_NAMES = """
            SELECT
                t.id, t.title, t.description, t.status,
                t.created_by, t.assigned_to,
                t.created_at, t.updated_at,
                assignee.name  AS assignee_name,
                reporter.name  AS reporter_name
            FROM tasks t
            LEFT JOIN users assignee ON t.assigned_to = assignee.id
            LEFT JOIN users reporter ON t.created_by  = reporter.id
            """;

    // ── findAll ───────────────────────────────────────────────────────────────

    @Override
    public List<Task> findAll() {
        return jdbcTemplate.query(
                SELECT_WITH_NAMES + " ORDER BY t.created_at DESC",
                rowMapper);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Override
    public Optional<Task> findById(Long id) {
        String sql = SELECT_WITH_NAMES + " WHERE t.id = ?";
        try {
            Task task = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(task);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── findByAssigneeId ──────────────────────────────────────────────────────

    /**
     * Returns all tasks assigned to a specific user.
     * Used by UpdateTaskStatusCommand to show only tasks the current user
     * can update (only assignees can change status).
     */
    @Override
    public List<Task> findByAssigneeId(Long assigneeId) {
        String sql = SELECT_WITH_NAMES + " WHERE t.assigned_to = ? ORDER BY t.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, assigneeId);
    }

    // ── save (INSERT / UPDATE) ────────────────────────────────────────────────

    @Override
    public Task save(Task task) {
        if (task.getId() == null) {
            // INSERT
            String sql = """
                    INSERT INTO tasks
                        (title, description, status, created_by, assigned_to, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, task.getTitle());
                ps.setString(2, task.getDescription());
                ps.setString(3, task.getStatus());
                ps.setLong(4, task.getCreatedBy());
                if (task.getAssignedTo() != null) {
                    ps.setLong(5, task.getAssignedTo());
                } else {
                    ps.setNull(5, java.sql.Types.BIGINT);
                }
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                return ps;
            }, keyHolder);
            task.setId(keyHolder.getKey().longValue());
        } else {
            // UPDATE
            String sql = """
                    UPDATE tasks
                    SET title = ?, description = ?, status = ?, assigned_to = ?, updated_at = ?
                    WHERE id = ?
                    """;
            jdbcTemplate.update(sql,
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getAssignedTo(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    task.getId());
        }
        return task;
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Override
    public boolean deleteById(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", id);
        return rows > 0;
    }
}