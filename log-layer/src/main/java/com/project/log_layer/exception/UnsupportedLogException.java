package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;

/**
 * Thrown when no parser supports the supplied log source.
 */
public class UnsupportedLogException extends LogException {

    public UnsupportedLogException(String source) {
        super(
                ErrorCode.UNSUPPORTED_LOG_SOURCE,
                "Unsupported log source: " + source
        );
    }

}