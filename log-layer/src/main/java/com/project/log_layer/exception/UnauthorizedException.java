package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;

public class UnauthorizedException extends LogException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}