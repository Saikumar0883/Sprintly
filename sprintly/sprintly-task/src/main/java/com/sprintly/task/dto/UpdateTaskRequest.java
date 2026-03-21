package com.sprintly.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Request DTO for updating a task.
 *
 * All fields are optional — only non-null fields are applied (partial update).
 *
 * Used by two endpoints:
 *   PUT   /api/tasks/{id}        → update title, description, assignedTo, status
 *   PATCH /api/tasks/{id}/status → update ONLY status (other fields ignored)
 *
 * Why @NotBlank was removed from title:
 *   The PATCH /status endpoint sends only { "status": "IN_PROGRESS" }.
 *   If title had @NotBlank, the PATCH call would fail validation
 *   because title is not sent. Since all fields are optional here,
 *   no @NotBlank is needed — the service layer handles null checks.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating a task. All fields are optional.")
public class UpdateTaskRequest {

    @Schema(description = "Task title", example = "Fix login redirect bug")
    private String title;

    @Schema(description = "Task description", example = "OAuth2 redirect fails on Safari 16+")
    private String description;

    @Schema(
            description = "Task status. Valid transitions: TODO→IN_PROGRESS→IN_REVIEW→DONE. " +
                    "IN_REVIEW can also go back to IN_PROGRESS. Any status can go to CANCELLED.",
            example = "IN_PROGRESS",
            allowableValues = {"TODO", "IN_PROGRESS", "IN_REVIEW", "DONE", "CANCELLED"}
    )
    private String status;

    @Schema(description = "User ID to assign this task to", example = "3")
    private Long assignedTo;
}