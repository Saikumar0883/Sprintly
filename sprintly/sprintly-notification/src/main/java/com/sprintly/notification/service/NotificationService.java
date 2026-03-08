package com.sprintly.notification.service;

import com.sprintly.notification.entity.Notification;
import com.sprintly.notification.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing notifications and real-time messaging.
 * Handles both database persistence and WebSocket broadcasting.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository repository,
                              SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Create and send a notification to a user.
     */
    @Transactional
    public Notification createAndSendNotification(String type, String title, String message,
                                                 Long recipientId, Long senderId,
                                                 Long entityId, String entityType) {
        Notification notification = Notification.builder()
            .type(type)
            .title(title)
            .message(message)
            .recipientId(recipientId)
            .senderId(senderId)
            .entityId(entityId)
            .entityType(entityType)
            .read(false)
            .createdAt(LocalDateTime.now())
            .build();

        Notification saved = repository.save(notification);

        // Send via WebSocket to the specific user
        sendToUser(recipientId, saved);

        return saved;
    }

    /**
     * Send notification to all online users (broadcast).
     */
    public void broadcastNotification(String type, String title, String message) {
        Notification notification = Notification.builder()
            .type(type)
            .title(title)
            .message(message)
            .read(false)
            .createdAt(LocalDateTime.now())
            .build();

        // Send to all connected clients
        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    /**
     * Send notification to a specific user via WebSocket.
     */
    private void sendToUser(Long userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notifications",
            notification
        );
    }

    /**
     * Get all notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(Long userId) {
        return repository.findByRecipientId(userId);
    }

    /**
     * Get unread notifications for a user.
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long userId) {
        return repository.findUnreadByRecipientId(userId);
    }

    /**
     * Mark a notification as read.
     */
    @Transactional
    public boolean markAsRead(Long notificationId) {
        boolean updated = repository.markAsRead(notificationId);
        if (updated) {
            // Notify client that notification was read
            Optional<Notification> notification = repository.findById(notificationId);
            notification.ifPresent(n -> sendToUser(n.getRecipientId(), n));
        }
        return updated;
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        int count = repository.markAllAsRead(userId);
        if (count > 0) {
            // Send updated notification count to user
            sendNotificationCount(userId);
        }
        return count;
    }

    /**
     * Send notification count to user.
     */
    private void sendNotificationCount(Long userId) {
        int unreadCount = repository.countUnreadByRecipientId(userId);
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notification-count",
            unreadCount
        );
    }

    /**
     * Delete a notification.
     */
    @Transactional
    public boolean deleteNotification(Long notificationId) {
        return repository.deleteById(notificationId);
    }

    /**
     * Delete all notifications for a user.
     */
    @Transactional
    public int deleteAllUserNotifications(Long userId) {
        return repository.deleteByRecipientId(userId);
    }

    /**
     * Get notification count for a user.
     */
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return repository.countUnreadByRecipientId(userId);
    }

    // Convenience methods for common notification types

    public void notifyTaskAssigned(Long taskId, String taskTitle, Long assigneeId, Long assignerId) {
        createAndSendNotification(
            "TASK_ASSIGNED",
            "Task Assigned",
            "You have been assigned to task: " + taskTitle,
            assigneeId,
            assignerId,
            taskId,
            "TASK"
        );
    }

    public void notifyTaskCompleted(Long taskId, String taskTitle, Long completerId) {
        // Notify task creator and assignee (if different)
        Optional<Notification> taskNotification = repository.findById(taskId);
        if (taskNotification.isPresent()) {
            Long creatorId = taskNotification.get().getSenderId();
            if (creatorId != null && !creatorId.equals(completerId)) {
                createAndSendNotification(
                    "TASK_COMPLETED",
                    "Task Completed",
                    "Task completed: " + taskTitle,
                    creatorId,
                    completerId,
                    taskId,
                    "TASK"
                );
            }
        }
    }

    public void notifyTaskOverdue(Long taskId, String taskTitle, Long assigneeId) {
        createAndSendNotification(
            "TASK_OVERDUE",
            "Task Overdue",
            "Task is overdue: " + taskTitle,
            assigneeId,
            null,
            taskId,
            "TASK"
        );
    }
}