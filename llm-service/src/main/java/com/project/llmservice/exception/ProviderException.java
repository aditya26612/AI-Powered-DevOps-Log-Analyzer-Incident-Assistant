package com.project.llmservice.exception;

public class ProviderException extends LlmException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}