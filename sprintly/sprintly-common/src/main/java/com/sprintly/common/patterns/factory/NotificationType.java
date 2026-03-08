package com.sprintly.common.patterns.factory;

/**
 * Notification types supported by the Factory.
 * Add new types here to extend without modifying existing code (Open/Closed Principle).
 */
public enum NotificationType {
    IN_APP,
    EMAIL,
    PUSH
}
