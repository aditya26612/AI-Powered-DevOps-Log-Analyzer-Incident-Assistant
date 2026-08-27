package com.project.llmservice.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelConstantsTest {

    @Test
    void shouldContainCorrectModelConstants() {

        assertEquals(
                "OLLAMA",
                ModelConstants.DEFAULT_PROVIDER
        );

        assertEquals(
                "llama2",
                ModelConstants.DEFAULT_MODEL
        );

        assertEquals(
                "CHAT",
                ModelConstants.MODEL_TYPE_CHAT
        );
    }
}