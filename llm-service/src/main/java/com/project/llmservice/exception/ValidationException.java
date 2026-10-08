package com.project.llmservice.exception;

public class ValidationException extends LlmException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}