package com.sprintly.task.mapper;

import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.entity.Task;

/**
 * Maps between Task entity and TaskDTO.
 *
 * P2 Fix: Maps the new assigneeName and createdByName fields
 * that are populated by JdbcTaskRepository's JOIN queries.
 */
public class TaskMapper {

    /**
     * Convert Task entity → TaskDTO for API responses.
     *
     * assigneeName and createdByName come from JdbcTaskRepository
     * JOIN queries. They will be null if:
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
                .createdBy(t.getCreatedBy())
                .createdByName(t.getCreatedByName())    // ← NEW
                .assignedTo(t.getAssignedTo())
                .assigneeName(t.getAssigneeName())      // ← NEW
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
                .createdBy(dto.getCreatedBy())
                .assignedTo(dto.getAssignedTo())
                .build();
    }
}
