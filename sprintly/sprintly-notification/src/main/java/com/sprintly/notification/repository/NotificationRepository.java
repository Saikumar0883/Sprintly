package com.sprintly.notification.repository;

import com.sprintly.notification.entity.Notification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Notification entity operations.
 * Implementation uses Spring JDBC for direct SQL queries.
 */
@Repository
public interface NotificationRepository {

    /**
     * Save a new notification or update an existing one.
     */
    Notification save(Notification notification);

    /**
     * Find a notification by ID.
     */
    Optional<Notification> findById(Long id);

    /**
     * Find all notifications for a specific user.
     */
    List<Notification> findByRecipientId(Long recipientId);

    /**
     * Find unread notifications for a specific user.
     */
    List<Notification> findUnreadByRecipientId(Long recipientId);

    /**
     * Mark a notification as read.
     */
    boolean markAsRead(Long notificationId);

    /**
     * Mark all notifications as read for a user.
     */
    int markAllAsRead(Long recipientId);

    /**
     * Delete a notification by ID.
     */
    boolean deleteById(Long id);

    /**
     * Delete all notifications for a user.
     */
    int deleteByRecipientId(Long recipientId);

    /**
     * Count unread notifications for a user.
     */
    int countUnreadByRecipientId(Long recipientId);
}