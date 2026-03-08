package com.sprintly.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for notification data transfer.
 * Used in REST API responses and WebSocket messages.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    private Long id;
    private String type;
    private String title;
    private String message;
    private Long recipientId;
    private Long senderId;
    private Long entityId;
    private String entityType;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}