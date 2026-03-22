package com.sprintly.task.mapper;

import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.entity.Task;

/**
 * Maps between Task entity and TaskDTO.
 *
 * Reporter mapping:
 *   reporterId   = task.createdBy (same DB value, domain-named alias)
 *   reporterName = task.reporterName (from LEFT JOIN in JdbcTaskRepository)
 */
public class TaskMapper {

    /**
     * Convert Task entity → TaskDTO for API responses.
     *
     * reporterName and assigneeName come from JdbcTaskRepository LEFT JOIN queries.
     * They will be null if:
     *   - assignedTo is null (unassigned task)
     *   - The joined user no longer exists in the DB
     */
    public static TaskDTO toDto(Task t) {
        if (t == null) return null;
        return TaskDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                // Reporter: use reporterId if set, fallback to createdBy
                .reporterId(t.getReporterId() != null ? t.getReporterId() : t.getCreatedBy())
                .reporterName(t.getReporterName() != null ? t.getReporterName() : t.getCreatedByName())
                .assignedTo(t.getAssignedTo())
                .assigneeName(t.getAssigneeName())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    /**
     * Build a Task entity from a TaskDTO (used internally).
     */
    public static Task fromDto(TaskDTO dto) {
        if (dto == null) return null;
        return Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .createdBy(dto.getReporterId())
                .reporterId(dto.getReporterId())
                .assignedTo(dto.getAssignedTo())
                .build();
    }
}