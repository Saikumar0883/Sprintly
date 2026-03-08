package com.sprintly.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all TaskFlow application exceptions.
 * Carries an HTTP status so the GlobalExceptionHandler can respond correctly.
 */
public class TaskFlowException extends RuntimeException {

    private final HttpStatus status;

    public TaskFlowException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
