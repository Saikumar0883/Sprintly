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

    private final RowMapper<Task> rowMapper = (rs, rowNum) -> {
        Task t = new Task();
        t.setId(rs.getLong("id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setStatus(rs.getString("status"));
        t.setCreatedBy(rs.getLong("created_by"));
        t.setAssignedTo(rs.getLong("assigned_to"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        t.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return t;
    };

    @Override
    public Optional<Task> findById(Long id) {
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try {
            Task task = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(task);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Task> findAll() {
        String sql = "SELECT * FROM tasks";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public Task save(Task task) {
        if (task.getId() == null) {
            String sql = "INSERT INTO tasks (title, description, status, created_by, assigned_to, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
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
            String sql = "UPDATE tasks SET title = ?, description = ?, status = ?, assigned_to = ?, updated_at = ? WHERE id = ?";
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

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        return rows > 0;
    }
}
