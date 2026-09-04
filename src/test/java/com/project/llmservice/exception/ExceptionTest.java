package com.project.llmservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void shouldCreateLlmExceptionWithMessage() {

        LlmException exception =
                new LlmException("LLM error");

        assertEquals("LLM error", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void shouldCreateLlmExceptionWithCause() {

        Throwable cause =
                new RuntimeException("root cause");

        LlmException exception =
                new LlmException("LLM error", cause);

        assertEquals("LLM error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldMaintainPromptExceptionHierarchy() {

        PromptException exception =
                new PromptException("Prompt error");

        assertInstanceOf(LlmException.class, exception);
        assertEquals("Prompt error", exception.getMessage());
    }

    @Test
    void shouldCreatePromptTemplateNotFoundException() {

        PromptTemplateNotFoundException exception =
                new PromptTemplateNotFoundException(
                        "Template not found"
                );

        assertInstanceOf(PromptException.class, exception);
        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Template not found",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreatePromptValidationException() {

        PromptValidationException exception =
                new PromptValidationException(
                        "Invalid prompt"
                );

        assertInstanceOf(PromptException.class, exception);
        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Invalid prompt",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateProviderException() {

        ProviderException exception =
                new ProviderException("Provider error");

        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Provider error",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateResponseParsingException() {

        ResponseParsingException exception =
                new ResponseParsingException("Parsing error");

        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Parsing error",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateRetrievalException() {

        RetrievalException exception =
                new RetrievalException("Retrieval error");

        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Retrieval error",
                exception.getMessage()
        );
    }

    @Test
    void shouldCreateValidationException() {

        ValidationException exception =
                new ValidationException("Validation error");

        assertInstanceOf(LlmException.class, exception);
        assertEquals(
                "Validation error",
                exception.getMessage()
        );
    }

    @Test
    void shouldPreserveCauseInChildException() {

        Throwable cause =
                new RuntimeException("root cause");

        ProviderException exception =
                new ProviderException(
                        "Provider failed",
                        cause
                );

        assertEquals(
                "Provider failed",
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }
}