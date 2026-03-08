package com.sprintly.task.dto;

import lombok.*;

import java.time.LocalDateTime;

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
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
