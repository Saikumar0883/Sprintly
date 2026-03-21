package com.sprintly.task.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Task entity corresponding to the `tasks` table.
 *
 * P2 Fix: Added assigneeName and createdByName fields.
 *
 * These are NOT columns in the tasks table — they come from
 * LEFT JOINs in JdbcTaskRepository. They are populated at
 * query time and carried through to TaskDTO for display.
 *
 * This is a common pattern with JDBC/JdbcTemplate:
 *   Entity carries both its own columns AND joined-in display fields.
 *   JPA would handle this differently via @ManyToOne relationships,
 *   but since we use raw JDBC, we carry the names directly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    private Long id;
    private String title;
    private String description;
    private String status;           // TODO, IN_PROGRESS, IN_REVIEW, DONE, CANCELLED

    private Long createdBy;          // FK → users.id
    private String createdByName;    // ← from JOIN: users.name WHERE id = created_by

    private Long assignedTo;         // FK → users.id (nullable — means unassigned)
    private String assigneeName;     // ← from JOIN: users.name WHERE id = assigned_to

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
