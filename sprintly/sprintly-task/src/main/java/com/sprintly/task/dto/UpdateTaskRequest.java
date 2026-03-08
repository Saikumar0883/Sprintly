package com.sprintly.task.dto;

import lombok.*;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskRequest {
    @NotBlank
    private String title;
    private String description;
    private String status;
    private Long assignedTo;
}
