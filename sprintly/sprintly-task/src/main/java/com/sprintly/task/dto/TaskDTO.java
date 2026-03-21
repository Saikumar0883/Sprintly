package com.sprintly.task.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Task — sent to all clients (REST + CLI).
 *
 * P2 Fix: Added assigneeName field.
 *
 * Why add assigneeName here instead of making CLI call /users/{id}?
 *
 *   Option A (without assigneeName):
 *     CLI receives { assignedTo: 5 }
 *     CLI calls GET /api/users/5 → gets name
 *     = N+1 problem: one extra HTTP call per task shown in the list
 *     = Slower, especially when listing 20+ tasks
 *
 *   Option B (with assigneeName) ← WE CHOSE THIS:
 *     Backend does a JOIN at the DB level (one SQL query)
 *     CLI receives { assignedTo: 5, assigneeName: "Ravi Kumar" }
 *     = Zero extra calls from CLI
 *     = Clean single response, ready to display
 *
 * The backend populates assigneeName via a JOIN in JdbcTaskRepository.
 * If assignedTo is null, assigneeName is also null.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDTO {
    private Long id;
    private String title;
    private String description;
    private String status;
    private Long createdBy;
    private String createdByName;    // name of the creator — for display
    private Long assignedTo;         // user ID of assignee (null = unassigned)
    private String assigneeName;     // ← NEW: name of the assignee (null = unassigned)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
