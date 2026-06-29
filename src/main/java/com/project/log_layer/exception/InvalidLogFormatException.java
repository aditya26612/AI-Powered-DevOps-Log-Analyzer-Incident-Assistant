package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;

/**
 * Thrown when a log cannot be parsed.
 */
public class InvalidLogFormatException extends LogException {

    public InvalidLogFormatException(String message) {
        super(
                ErrorCode.INVALID_LOG_FORMAT,
                message
        );
    }

}