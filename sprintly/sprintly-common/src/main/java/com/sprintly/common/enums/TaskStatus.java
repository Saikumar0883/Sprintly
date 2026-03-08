package com.sprintly.common.enums;

/**
 * Task lifecycle states.
 * Strategy pattern uses these to validate legal transitions.
 *
 *  TODO ──► IN_PROGRESS ──► IN_REVIEW ──► DONE
 *                │                          │
 *                └──────────────────────────► CANCELLED
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    CANCELLED
}
