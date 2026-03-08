package com.sprintly.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown for invalid JWT, missing roles — maps to HTTP 401 */
public class UnauthorizedException extends TaskFlowException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
