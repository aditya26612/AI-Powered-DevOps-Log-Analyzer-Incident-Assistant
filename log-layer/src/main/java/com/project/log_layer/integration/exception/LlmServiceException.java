package com.project.log_layer.integration.exception;

public class LlmServiceException extends RuntimeException {
    private final Integer httpStatus;
    private final boolean invalidResponse;

    public LlmServiceException(String message) {
        this(message, null, null, false);
    }

    public LlmServiceException(String message, Throwable cause) {
        this(message, cause, null, false);
    }

    private LlmServiceException(String message, Throwable cause, Integer status, boolean invalidResponse) {
        super(message, cause);
        this.httpStatus = status;
        this.invalidResponse = invalidResponse;
    }

    public static LlmServiceException http(int status) {
        return new LlmServiceException("LLM HTTP " + status, null, status, false);
    }

    public static LlmServiceException malformed() {
        return new LlmServiceException("Invalid LLM response", null, null, true);
    }

    public Integer httpStatus() { return httpStatus; }
    public boolean invalidResponse() { return invalidResponse; }
}
