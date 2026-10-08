package com.project.llmservice.exception;

public class ResponseParsingException extends LlmException {

    public ResponseParsingException(String message) {
        super(message);
    }

    public ResponseParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}