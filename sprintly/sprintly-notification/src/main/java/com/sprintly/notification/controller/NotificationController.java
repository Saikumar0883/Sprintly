package com.sprintly.notification.controller;

import com.sprintly.notification.dto.NotificationDTO;
import com.sprintly.notification.mapper.NotificationMapper;
import com.sprintly.notification.service.NotificationService;
import com.sprintly.common.dto.ApiResponse;
import com.sprintly.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for notification management.
 *
 * All endpoints require JWT authentication.
 * Each user can only see and manage their OWN notifications.
 *
 * The CLI uses these endpoints:
 *   GET  /unread        → sprintly notification unread
 *   GET  /              → sprintly notification list
 *   PUT  /{id}/read     → sprintly notification read <id>
 *   PUT  /read-all      → sprintly notification read-all
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(
        name = "Notifications",
        description = "Manage real-time notifications. Notifications are created automatically " +
                "when tasks are assigned. Use these endpoints to view and mark them as read. " +
                "WebSocket: subscribe to /user/queue/notifications for real-time delivery."
)
@SecurityRequirement(name = "BearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService,
                                  UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // ── Get All ───────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get all my notifications",
            description = "Returns all notifications (read and unread) for the authenticated user, " +
                    "ordered by creation time descending (newest first). " +
                    "Used by: sprintly notification list"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Notifications retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated — provide Bearer token")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getMyNotifications(
            Principal principal) {
        Long userId = getCurrentUserId(principal);
        List<NotificationDTO> notifications = notificationService
                .getUserNotifications(userId)
                .stream()
                .map(NotificationMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(
                ApiResponse.success("Notifications retrieved successfully", notifications));
    }

    // ── Get Unread ────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get unread notifications",
            description = "Returns only unread notifications for the authenticated user. " +
                    "Call PUT /read-all after viewing to acknowledge them. " +
                    "Used by: sprintly notification unread"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Unread notifications retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getUnreadNotifications(
            Principal principal) {
        Long userId = getCurrentUserId(principal);
        List<NotificationDTO> notifications = notificationService
                .getUnreadNotifications(userId)
                .stream()
                .map(NotificationMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(
                ApiResponse.success("Unread notifications retrieved successfully", notifications));
    }

    // ── Get Unread Count ──────────────────────────────────────────────────────

    @Operation(
            summary = "Get unread notification count",
            description = "Returns the count of unread notifications. " +
                    "Useful for showing a badge count in UI or CLI."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Count retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Integer>> getUnreadCount(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Unread count retrieved successfully", count));
    }

    // ── Mark One as Read ──────────────────────────────────────────────────────

    @Operation(
            summary = "Mark a notification as read",
            description = "Marks a single notification as read by its ID. " +
                    "Sets read=true and read_at=now() in the database. " +
                    "Used by: sprintly notification read <id>"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Notification marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Notification not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Boolean>> markAsRead(
            @Parameter(description = "Notification ID", example = "3")
            @PathVariable Long id) {
        boolean success = notificationService.markAsRead(id);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Notification marked as read", true));
        }
        return ResponseEntity.notFound().build();
    }

    // ── Mark All as Read ──────────────────────────────────────────────────────

    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks ALL unread notifications as read for the authenticated user. " +
                    "Returns the count of notifications that were updated. " +
                    "Used by: sprintly notification read-all"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "All notifications marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(
                ApiResponse.success(count + " notifications marked as read", count));
    }

    // ── Delete One ────────────────────────────────────────────────────────────

    @Operation(
            summary = "Delete a notification",
            description = "Permanently deletes a single notification by ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "Notification deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Notification not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @Parameter(description = "Notification ID", example = "3")
            @PathVariable Long id) {
        boolean deleted = notificationService.deleteNotification(id);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }

    // ── Delete All ────────────────────────────────────────────────────────────

    @Operation(
            summary = "Delete all my notifications",
            description = "Permanently deletes ALL notifications for the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "All notifications deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Not authenticated")
    })
    @DeleteMapping
    public ResponseEntity<ApiResponse<Integer>> deleteAllNotifications(Principal principal) {
        Long userId = getCurrentUserId(principal);
        int count = notificationService.deleteAllUserNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(count + " notifications deleted", count));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long getCurrentUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Authenticated user not found: " + principal.getName()));
    }
}