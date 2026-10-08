package com.project.llmservice.advice;

import com.project.llmservice.dto.response.ErrorResponse;
import com.project.llmservice.exception.LlmException;
import com.project.llmservice.exception.PromptException;
import com.project.llmservice.exception.PromptTemplateNotFoundException;
import com.project.llmservice.exception.ProviderException;
import com.project.llmservice.exception.ResponseParsingException;
import com.project.llmservice.exception.RetrievalException;
import com.project.llmservice.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PromptTemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePromptTemplateNotFound(
            PromptTemplateNotFoundException exception) {

        return buildResponse(
                "PROMPT_TEMPLATE_NOT_FOUND",
                exception.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(PromptException.class)
    public ResponseEntity<ErrorResponse> handlePromptException(
            PromptException exception) {

        return buildResponse(
                "PROMPT_ERROR",
                exception.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ErrorResponse> handleProviderException(
            ProviderException exception) {

        return buildResponse(
                "LLM_PROVIDER_ERROR",
                exception.getMessage(),
                HttpStatus.BAD_GATEWAY
        );
    }

    @ExceptionHandler(ResponseParsingException.class)
    public ResponseEntity<ErrorResponse> handleResponseParsingException(
            ResponseParsingException exception) {

        return buildResponse(
                "RESPONSE_PARSING_ERROR",
                exception.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(RetrievalException.class)
    public ResponseEntity<ErrorResponse> handleRetrievalException(
            RetrievalException exception) {

        return buildResponse(
                "RETRIEVAL_ERROR",
                exception.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException exception) {

        return buildResponse(
                "VALIDATION_ERROR",
                exception.getMessage(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> handleLlmException(
            LlmException exception) {

        return buildResponse(
                "LLM_ERROR",
                exception.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            String code,
            String message,
            HttpStatus status) {

        ErrorResponse response = ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(status)
                .body(response);
    }
}