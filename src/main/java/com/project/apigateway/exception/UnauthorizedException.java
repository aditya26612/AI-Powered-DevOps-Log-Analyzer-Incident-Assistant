package com.project.apigateway.exception;

public class UnauthorizedException extends GatewayException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}