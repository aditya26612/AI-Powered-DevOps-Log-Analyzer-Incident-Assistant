package com.project.llmservice.exception;

public class PromptValidationException extends PromptException {

    public PromptValidationException(String message) {
        super(message);
    }

    public PromptValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}