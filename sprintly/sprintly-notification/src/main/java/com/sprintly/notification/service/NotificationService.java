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

    // ── Core create + send ────────────────────────────────────────────────────

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
        sendToUser(recipientId, saved);
        return saved;
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    public void broadcastNotification(String type, String title, String message) {
        Notification notification = Notification.builder()
                .type(type).title(title).message(message)
                .read(false).createdAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    // ── WebSocket helper ──────────────────────────────────────────────────────

    private void sendToUser(Long userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                notification
        );
    }

    private void sendNotificationCount(Long userId) {
        int unreadCount = repository.countUnreadByRecipientId(userId);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notification-count",
                unreadCount
        );
    }

    // ── Read / unread ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(Long userId) {
        return repository.findByRecipientId(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(Long userId) {
        return repository.findUnreadByRecipientId(userId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return repository.countUnreadByRecipientId(userId);
    }

    @Transactional
    public boolean markAsRead(Long notificationId) {
        boolean updated = repository.markAsRead(notificationId);
        if (updated) {
            Optional<Notification> notification = repository.findById(notificationId);
            notification.ifPresent(n -> sendToUser(n.getRecipientId(), n));
        }
        return updated;
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        int count = repository.markAllAsRead(userId);
        if (count > 0) sendNotificationCount(userId);
        return count;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public boolean deleteNotification(Long notificationId) {
        return repository.deleteById(notificationId);
    }

    @Transactional
    public int deleteAllUserNotifications(Long userId) {
        return repository.deleteByRecipientId(userId);
    }

    // ── Domain notification helpers ───────────────────────────────────────────

    /**
     * Sent to the assignee when a task is assigned to them.
     */
    public void notifyTaskAssigned(Long taskId, String taskTitle,
                                   Long assigneeId, Long assignerId) {
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

    /**
     * Sent to the REPORTER when the assignee marks a task as DONE.
     *
     * Req 2 fix:
     *   Old notifyTaskCompleted() tried to look up reporterId from a Notification
     *   record — which was wrong and unreliable. This new method receives
     *   reporterId directly from TaskService where the task entity is available.
     *
     * @param taskId     ID of the completed task
     * @param taskTitle  title of the completed task (shown in notification message)
     * @param assigneeId ID of the user who marked it DONE (shown in message)
     * @param reporterId ID of the reporter/creator who receives this notification
     */
    public void notifyTaskDone(Long taskId, String taskTitle,
                               Long assigneeId, Long reporterId) {
        createAndSendNotification(
                "TASK_DONE",
                "Task Completed",
                "Task \"" + taskTitle + "\" has been marked as DONE.",
                reporterId,   // recipient = reporter
                assigneeId,   // sender = assignee who completed it
                taskId,
                "TASK"
        );
    }

    /**
     * Sent to the assignee when a task is overdue.
     */
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