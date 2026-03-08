package com.sprintly.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Structured error response returned by GlobalExceptionHandler.
 *
 * Design Pattern: Builder
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;

    /** Field-level validation errors, e.g. { "email": "must not be blank" } */
    private final Map<String, String> fieldErrors;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();
}
