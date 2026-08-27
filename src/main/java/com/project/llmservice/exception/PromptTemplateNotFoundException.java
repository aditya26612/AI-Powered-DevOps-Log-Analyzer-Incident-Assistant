package com.project.llmservice.exception;

public class PromptTemplateNotFoundException extends PromptException {

    public PromptTemplateNotFoundException(String message) {
        super(message);
    }

    public PromptTemplateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}