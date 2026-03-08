package com.sprintly.notification.mapper;

import com.sprintly.notification.dto.NotificationDTO;
import com.sprintly.notification.entity.Notification;

/**
 * Static mapper methods for converting between Notification entities and DTOs.
 */
public class NotificationMapper {

    /**
     * Convert Notification entity to NotificationDTO.
     */
    public static NotificationDTO toDto(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationDTO.builder()
            .id(notification.getId())
            .type(notification.getType())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .recipientId(notification.getRecipientId())
            .senderId(notification.getSenderId())
            .entityId(notification.getEntityId())
            .entityType(notification.getEntityType())
            .read(notification.isRead())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .build();
    }

    /**
     * Convert NotificationDTO to Notification entity.
     */
    public static Notification toEntity(NotificationDTO dto) {
        if (dto == null) {
            return null;
        }

        return Notification.builder()
            .id(dto.getId())
            .type(dto.getType())
            .title(dto.getTitle())
            .message(dto.getMessage())
            .recipientId(dto.getRecipientId())
            .senderId(dto.getSenderId())
            .entityId(dto.getEntityId())
            .entityType(dto.getEntityType())
            .read(dto.isRead())
            .createdAt(dto.getCreatedAt())
            .readAt(dto.getReadAt())
            .build();
    }
}