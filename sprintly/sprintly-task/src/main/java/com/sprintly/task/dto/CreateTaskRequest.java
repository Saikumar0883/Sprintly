package com.sprintly.task.dto;

import lombok.*;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskRequest {
    @NotBlank
    private String title;
    private String description;
    private Long assignedTo;   // optional user id
}
