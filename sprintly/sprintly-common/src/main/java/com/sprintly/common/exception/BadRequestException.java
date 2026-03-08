package com.sprintly.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown for invalid input or illegal state transitions — maps to HTTP 400 */
public class BadRequestException extends TaskFlowException {
    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
