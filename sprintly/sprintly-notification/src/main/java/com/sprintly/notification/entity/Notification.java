package com.sprintly.notification.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification entity representing real-time alerts and messages.
 * Stored in database and sent via WebSocket to connected clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    private Long id;
    private String type;        // TASK_ASSIGNED, TASK_COMPLETED, TASK_OVERDUE, etc.
    private String title;
    private String message;
    private Long recipientId;   // User who should receive this notification
    private Long senderId;      // User who triggered this notification (optional)
    private Long entityId;      // ID of related entity (task, comment, etc.)
    private String entityType;  // TASK, COMMENT, USER, etc.
    private boolean read;       // Has the user read this notification?
    private LocalDateTime createdAt;
    private LocalDateTime readAt; // When the notification was read (optional)
}