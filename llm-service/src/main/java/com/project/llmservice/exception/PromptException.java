package com.project.llmservice.exception;

public class PromptException extends LlmException {

    public PromptException(String message) {
        super(message);
    }

    public PromptException(String message, Throwable cause) {
        super(message, cause);
    }
}