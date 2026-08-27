package com.project.llmservice.exception;

public class RetrievalException extends LlmException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}