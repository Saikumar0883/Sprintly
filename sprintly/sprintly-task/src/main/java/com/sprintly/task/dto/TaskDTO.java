package com.sprintly.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Task Data Transfer Object — sent in all API responses (REST + CLI).
 *
 * Reporter fields:
 *   reporterId   = same as createdBy (auto-set from creator, never user-provided)
 *   reporterName = display name of the reporter (from SQL JOIN, no extra call needed)
 *
 * Assignee fields:
 *   assignedTo   = user ID of the person the task is assigned to
 *   assigneeName = display name (from SQL JOIN)
 *
 * Both names are resolved server-side via LEFT JOIN in JdbcTaskRepository
 * so CLI/Swagger never need extra calls to look up names.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Task details")
public class TaskDTO {

    @Schema(description = "Task ID", example = "1")
    private Long id;

    @Schema(description = "Task title", example = "Fix login redirect bug")
    private String title;

    @Schema(description = "Task description", example = "OAuth2 redirect fails on Safari 16+")
    private String description;

    @Schema(description = "Current status",
            allowableValues = {"TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED"},
            example = "TODO")
    private String status;

    // ── Reporter (creator) ────────────────────────────────────────────────────

    @Schema(description = "Reporter user ID — the person who created the task. Auto-set from logged-in user, never user-provided.", example = "2")
    private Long reporterId;

    @Schema(description = "Reporter display name", example = "Saikumar")
    private String reporterName;

    // ── Assignee ──────────────────────────────────────────────────────────────

    @Schema(description = "Assignee user ID — who the task is assigned to (null = unassigned)", example = "3")
    private Long assignedTo;

    @Schema(description = "Assignee display name", example = "Anil")
    private String assigneeName;

    @Schema(description = "Task creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp")
    private LocalDateTime updatedAt;
}