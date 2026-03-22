package com.sprintly.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request DTO for bulk status update.
 *
 * Allows an assignee to update the status of multiple tasks in one API call.
 *
 * Example:
 * {
 *   "taskIds": [1, 2, 3],
 *   "status": "IN_PROGRESS"
 * }
 *
 * Rules (enforced in TaskService.bulkUpdateStatus):
 *   - Caller must be the assignee of ALL tasks in the list
 *   - The new status must be a valid transition from each task's current status
 *   - Tasks that fail validation are reported individually — others still succeed
 *
 * Used by:
 *   PATCH /api/tasks/bulk-status          (REST / Swagger)
 *   sprintly task bulk-status             (CLI)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update the status of multiple tasks at once")
public class BulkStatusRequest {

    @NotEmpty(message = "taskIds must not be empty")
    @Schema(description = "List of task IDs to update", example = "[1, 2, 3]")
    private List<Long> taskIds;

    @NotNull(message = "status is required")
    @Schema(
            description = "New status to apply to all listed tasks",
            example = "IN_PROGRESS",
            allowableValues = {"TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED"}
    )
    private String status;
}