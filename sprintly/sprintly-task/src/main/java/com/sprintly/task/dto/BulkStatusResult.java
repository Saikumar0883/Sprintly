package com.sprintly.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * Response DTO for bulk status update.
 *
 * Reports which tasks succeeded and which failed, with reasons.
 * Allows partial success — some tasks update even if others fail.
 *
 * Example response:
 * {
 *   "successCount": 2,
 *   "failureCount": 1,
 *   "updatedTasks": [...],
 *   "failures": [
 *     { "taskId": 3, "reason": "You are not the assignee of task #3" }
 *   ]
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of a bulk status update operation")
public class BulkStatusResult {

    @Schema(description = "Number of tasks successfully updated", example = "2")
    private int successCount;

    @Schema(description = "Number of tasks that failed to update", example = "1")
    private int failureCount;

    @Schema(description = "Details of successfully updated tasks")
    private List<TaskDTO> updatedTasks;

    @Schema(description = "Details of failed updates")
    private List<FailureDetail> failures;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Details of a single failed task update")
    public static class FailureDetail {

        @Schema(description = "Task ID that failed", example = "3")
        private Long taskId;

        @Schema(description = "Reason for failure", example = "You are not the assignee of task #3")
        private String reason;
    }
}