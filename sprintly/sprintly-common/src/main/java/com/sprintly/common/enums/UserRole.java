package com.sprintly.common.enums;

/**
 * Role hierarchy: ADMIN > MANAGER > DEVELOPER
 *
 * ADMIN     — full access, user management
 * MANAGER   — create/delete tasks, assign users, view all
 * DEVELOPER — create/update own tasks, view assigned tasks
 */
public enum UserRole {
    ROLE_ADMIN,
    ROLE_MANAGER,
    ROLE_DEVELOPER
}
