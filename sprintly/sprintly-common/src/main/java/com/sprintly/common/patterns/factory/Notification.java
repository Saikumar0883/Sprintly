package com.sprintly.common.patterns.factory;

/**
 * Design Pattern: Factory Method
 *
 * Purpose:
 *   Decouples notification creation from the code that uses notifications.
 *   The caller asks the factory for a notification object without knowing
 *   the concrete class being instantiated.
 *
 * Structure:
 *   Notification (interface)
 *     ├── InAppNotification
 *     ├── EmailNotification
 *     └── PushNotification
 *
 *   NotificationFactory.create(type) → returns the right implementation
 *
 * Interview Note:
 *   "How do you add a new notification type?"
 *   → Add enum value + new class + one case in the factory switch.
 *     Zero changes to existing notification code (Open/Closed Principle).
 */
public interface Notification {

    /**
     * Send the notification to a recipient.
     * @param recipientId  user ID of the recipient
     * @param subject      short title / subject
     * @param body         detailed message body
     */
    void send(Long recipientId, String subject, String body);

    /** Human-readable label for logging / auditing */
    String getType();
}


// ─── Concrete Implementations ─────────────────────────────────────────────────

class InAppNotification implements Notification {

    @Override
    public void send(Long recipientId, String subject, String body) {
        // In real implementation: persist to notifications table,
        // then push via WebSocket to the connected user session.
        System.out.printf("[IN-APP] To userId=%d | %s: %s%n", recipientId, subject, body);
    }

    @Override
    public String getType() {
        return "IN_APP";
    }
}


class EmailNotification implements Notification {

    @Override
    public void send(Long recipientId, String subject, String body) {
        // In real implementation: look up user email, call JavaMailSender
        System.out.printf("[EMAIL] To userId=%d | Subject: %s | Body: %s%n", recipientId, subject, body);
    }

    @Override
    public String getType() {
        return "EMAIL";
    }
}


class PushNotification implements Notification {

    @Override
    public void send(Long recipientId, String subject, String body) {
        // In real implementation: call Firebase FCM or APNs
        System.out.printf("[PUSH] To userId=%d | %s: %s%n", recipientId, subject, body);
    }

    @Override
    public String getType() {
        return "PUSH";
    }
}
