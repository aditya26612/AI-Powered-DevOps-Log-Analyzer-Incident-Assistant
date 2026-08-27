package com.project.llmservice.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderTypeTest {

    @Test
    void shouldContainOllamaProvider() {

        assertEquals(
                1,
                ProviderType.values().length
        );

        assertEquals(
                ProviderType.OLLAMA,
                ProviderType.valueOf("OLLAMA")
        );
    }
}