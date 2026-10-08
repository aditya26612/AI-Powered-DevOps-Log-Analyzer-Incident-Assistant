package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;

/**
 * Thrown when a log cannot be found.
 */
public class LogNotFoundException extends LogException {

    public LogNotFoundException(String id) {
        super(
                ErrorCode.LOG_NOT_FOUND,
                "Log not found with id: " + id
        );
    }

}