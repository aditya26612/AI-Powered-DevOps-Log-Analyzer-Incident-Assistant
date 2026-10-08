package com.project.llmservice.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationPropertiesTest {

    @Test
    void shouldSetProvider() {

        ApplicationProperties properties =
                new ApplicationProperties();

        properties.setProvider("OLLAMA");

        assertEquals(
                "OLLAMA",
                properties.getProvider()
        );
    }

    @Test
    void shouldAllowUnsetProvider() {

        ApplicationProperties properties =
                new ApplicationProperties();

        assertNull(
                properties.getProvider()
        );
    }
}