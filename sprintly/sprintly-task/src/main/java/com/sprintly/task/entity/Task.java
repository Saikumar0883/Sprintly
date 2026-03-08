package com.sprintly.task.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Simple Task entity corresponding to the `tasks` table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    private Long id;
    private String title;
    private String description;
    private String status;        // TODO, IN_PROGRESS, DONE, etc.
    private Long createdBy;       // user id
    private Long assignedTo;      // user id or null
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
