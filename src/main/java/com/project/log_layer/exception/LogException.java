package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;
import lombok.Getter;

/**
 * Base exception for all Log Layer exceptions.
 */
@Getter
public abstract class LogException extends RuntimeException {

    private final ErrorCode errorCode;

    protected LogException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected LogException(ErrorCode errorCode,
                           String message,
                           Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}