package com.sprintly.task.entity;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Task entity corresponding to the `tasks` table.
 *
 * Reporter = the person who created the task.
 *   - reporterId maps to DB column `created_by` (same column, explicit name)
 *   - reporterName comes from LEFT JOIN on users table (not a DB column)
 *   - No new DB column needed — reporter IS the creator
 *
 * assigneeName / reporterName / createdByName:
 *   Not stored in DB — populated via LEFT JOIN in JdbcTaskRepository.
 *   Avoids N+1 API calls from the CLI.
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

    // Creator / Reporter (same person, two names for domain clarity)
    private Long createdBy;          // DB column: created_by
    private Long reporterId;         // Same value as createdBy — explicit reporter concept
    private String createdByName;    // From LEFT JOIN users on created_by (legacy display)
    private String reporterName;     // Same as createdByName — reporter-named alias

    // Assignee
    private Long assignedTo;         // DB column: assigned_to (nullable = unassigned)
    private String assigneeName;     // From LEFT JOIN users on assigned_to

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}