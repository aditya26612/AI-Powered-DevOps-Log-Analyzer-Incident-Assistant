package com.project.apigateway.exception;

public class InvalidJwtException extends GatewayException {

    public InvalidJwtException(String message) {
        super(message);
    }

    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}