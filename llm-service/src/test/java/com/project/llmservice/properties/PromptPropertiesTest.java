package com.project.llmservice.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptPropertiesTest {

    @Test
    void shouldSetMaxContextLength() {

        PromptProperties properties =
                new PromptProperties();

        properties.setMaxContextLength(8000);

        assertEquals(
                8000,
                properties.getMaxContextLength()
        );
    }

    @Test
    void shouldAllowUnsetMaxContextLength() {

        PromptProperties properties =
                new PromptProperties();

        assertNull(properties.getMaxContextLength());
    }
}