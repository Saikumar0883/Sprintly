package com.sprintly.common.patterns.factory;

/**
 * Factory that creates the correct Notification implementation.
 *
 * Design Pattern: Factory Method / Static Factory
 *
 * Usage:
 *   Notification n = NotificationFactory.create(NotificationType.EMAIL);
 *   n.send(userId, "Task Assigned", "You have been assigned TASK-42");
 */
public class NotificationFactory {

    /** Private constructor — this is a utility/factory class, not instantiatable */
    private NotificationFactory() {}

    /**
     * Creates and returns the appropriate Notification implementation.
     *
     * @param type the desired notification channel
     * @return a Notification instance ready to call .send() on
     * @throws IllegalArgumentException for unknown types
     */
    public static Notification create(NotificationType type) {
        return switch (type) {
            case IN_APP -> new InAppNotification();
            case EMAIL  -> new EmailNotification();
            case PUSH   -> new PushNotification();
        };
    }
}
