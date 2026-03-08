package com.sprintly.notification.controller;

import com.sprintly.notification.dto.NotificationDTO;
import com.sprintly.notification.service.NotificationService;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for notification management.
 * Provides endpoints for retrieving and managing user notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /**
     * Get all notifications for the authenticated user.
     */
    @GetMapping
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getMyNotifications(Principal principal) {
        Long userId = getCurrentUserId(principal);
        List<NotificationDTO> notifications = notificationService.getUserNotifications(userId)
            .stream()
            .map(com.taskflow.notification.mapper.NotificationMapper::toDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", notifications));
    }

    /**
     * Get unread notifications for the authenticated user.
     */
    @GetMapping("/unread")
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getUnreadNotifications(Principal principal) {
        Long userId = getCurrentUserId(principal);
        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId)
            .stream()
            .map(com.taskflow.notification.mapper.NotificationMapper::toDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Unread notifications retrieved successfully", notifications));
    }

    /**
     * Get unread notification count for the authenticated user.
     */
    @GetMapping("/unread/count")
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> getUnreadCount(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved successfully", count));
    }

    /**
     * Mark a specific notification as read.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> markAsRead(@PathVariable Long id) {
        boolean success = notificationService.markAsRead(id);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Notification marked as read", true));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Mark all notifications as read for the authenticated user.
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.markAllAsRead(userId);

        return ResponseEntity.ok(ApiResponse.success(count + " notifications marked as read", count));
    }

    /**
     * Delete a specific notification.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        boolean deleted = notificationService.deleteNotification(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete all notifications for the authenticated user.
     */
    @DeleteMapping
    @PreAuthorize("hasRole('DEVELOPER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deleteAllNotifications(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.deleteAllUserNotifications(userId);

        return ResponseEntity.ok(ApiResponse.success(count + " notifications deleted", count));
    }

    /**
     * Extract current user ID from Principal.
     * The Principal name contains the user's email from the JWT token.
     */
    private Long getCurrentUserId(Principal principal) {
        String userEmail = principal.getName();
        return userRepository.findByEmail(userEmail)
            .map(user -> user.getId())
            .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + userEmail));
    }
}