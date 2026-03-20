package com.sprintly.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Structured error response returned by GlobalExceptionHandler.
 *
 * Design Pattern: Builder
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;

    /** Field-level validation errors, e.g. { "email": "must not be blank" } */
    private Map<String, String> fieldErrors;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
