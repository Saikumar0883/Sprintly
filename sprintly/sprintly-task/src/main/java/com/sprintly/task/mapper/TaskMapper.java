package com.sprintly.task.mapper;

import com.sprintly.task.dto.TaskDTO;
import com.sprintly.task.entity.Task;

public class TaskMapper {

    public static TaskDTO toDto(Task t) {
        if (t == null) return null;
        return TaskDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .createdBy(t.getCreatedBy())
                .assignedTo(t.getAssignedTo())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    public static Task fromCreateRequest(TaskDTO dto) {
        if (dto == null) return null;
        return Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .createdBy(dto.getCreatedBy())
                .assignedTo(dto.getAssignedTo())
                .build();
    }

    public static void updateEntity(Task task, TaskDTO dto) {
        if (dto.getTitle() != null) task.setTitle(dto.getTitle());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getStatus() != null) task.setStatus(dto.getStatus());
        if (dto.getAssignedTo() != null) task.setAssignedTo(dto.getAssignedTo());
    }
}