package com.project.log_layer.dto.response.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.project.log_layer.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard API error response.
 *
 * <p>
 * All exceptions handled by the GlobalExceptionHandler
 * are converted into this response format.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * HTTP status code.
     *
     * Example:
     * 400
     * 404
     * 500
     */
    private Integer status;

    /**
     * Standard HTTP error name.
     *
     * Example:
     * Bad Request
     * Not Found
     * Internal Server Error
     */
    private String error;

    /**
     * Application-specific error code.
     *
     * Example:
     * LOG_NOT_FOUND
     * INVALID_LOG_FORMAT
     * UNSUPPORTED_LOG_SOURCE
     * VALIDATION_FAILED
     */
    private ErrorCode errorCode;

    /**
     * Human-readable error message.
     */
    private String message;

    /**
     * API endpoint that generated the error.
     *
     * Example:
     * /api/v1/logs/15
     */
    private String path;

    /**
     * Validation errors.
     *
     * Populated only for validation failures.
     */
    private List<String> validationErrors;

}