package com.sprintly.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a DB entity cannot be found — maps to HTTP 404 */
public class ResourceNotFoundException extends TaskFlowException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
