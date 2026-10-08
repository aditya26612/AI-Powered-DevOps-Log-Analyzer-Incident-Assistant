package com.project.log_layer.enums;

/**
 * Application-specific error codes.
 *
 * Used for consistent error handling across the application.
 */
public enum ErrorCode {

    LOG_NOT_FOUND,

    INVALID_LOG_FORMAT,

    UNSUPPORTED_LOG_SOURCE,

    VALIDATION_FAILED,

    INVALID_SEARCH_CRITERIA,

    ML_SERVICE_UNAVAILABLE,

    LLM_SERVICE_UNAVAILABLE,

    UNAUTHORIZED,

    INTERNAL_SERVER_ERROR
}