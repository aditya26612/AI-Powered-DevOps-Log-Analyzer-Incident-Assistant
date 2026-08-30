package com.project.llmservice.prompt;

import com.project.llmservice.exception.PromptValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptValidatorTest {

    private final PromptValidator validator =
            new PromptValidator();

    @Test
    void shouldAcceptValidPrompt() {

        assertDoesNotThrow(() ->
                validator.validate(
                        "Analyze this ERROR from payment-service."
                )
        );
    }

    @Test
    void shouldRejectNullPrompt() {

        assertThrows(
                PromptValidationException.class,
                () -> validator.validate(null)
        );
    }

    @Test
    void shouldRejectBlankPrompt() {

        assertThrows(
                PromptValidationException.class,
                () -> validator.validate("   ")
        );
    }

    @Test
    void shouldRejectUnresolvedPlaceholder() {

        assertThrows(
                PromptValidationException.class,
                () -> validator.validate(
                        "Analyze ${message}"
                )
        );
    }
}