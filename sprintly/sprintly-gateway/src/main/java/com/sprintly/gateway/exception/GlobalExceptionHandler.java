package com.sprintly.gateway.exception;

import com.sprintly.common.dto.ErrorResponse;
import com.sprintly.common.exception.TaskFlowException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for the entire application.
 *
 * Added handlers:
 *
 *   IllegalStateException → 403 Forbidden
 *     Thrown by TaskService.updateTaskStatus() when the caller is not the assignee.
 *     "Only the assignee can update the status of task #X"
 *
 *   IllegalArgumentException → 400 Bad Request
 *     Thrown by TaskService.updateTaskStatus() for invalid status transitions.
 *     "Invalid status transition: TODO → DONE"
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── TaskFlowException (our custom hierarchy) ──────────────────────────────

    @ExceptionHandler(TaskFlowException.class)
    public ResponseEntity<ErrorResponse> handleTaskFlowException(
            TaskFlowException ex, HttpServletRequest request) {
        log.warn("TaskFlowException: {} — {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    // ── IllegalStateException → 403 ───────────────────────────────────────────

    /**
     * Thrown by TaskService when a non-assignee tries to update task status.
     * Maps to HTTP 403 Forbidden so both Swagger UI and CLI get a clean error message.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Forbidden action at {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ── IllegalArgumentException → 400 ───────────────────────────────────────

    /**
     * Thrown by TaskService when an invalid status transition is requested.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request at {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    // ── @Valid validation failures → 400 ─────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a
                ));
        log.warn("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields are invalid")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    // ── Spring Security @PreAuthorize → 403 ──────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to perform this action")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ── Catch-all → 500 ──────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again later.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}