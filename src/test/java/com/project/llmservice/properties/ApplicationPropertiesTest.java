package com.project.llmservice.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationPropertiesTest {

    @Test
    void shouldSetApplicationProperties() {

        ApplicationProperties properties =
                new ApplicationProperties();

        properties.setProvider("OLLAMA");

        ApplicationProperties.Timeout timeout =
                new ApplicationProperties.Timeout();

        timeout.setSeconds(60);
        properties.setTimeout(timeout);

        assertEquals("OLLAMA", properties.getProvider());
        assertNotNull(properties.getTimeout());
        assertEquals(60, properties.getTimeout().getSeconds());
    }

    @Test
    void shouldInitializeTimeoutByDefault() {

        ApplicationProperties properties =
                new ApplicationProperties();

        assertNotNull(properties.getTimeout());
        assertEquals(0, properties.getTimeout().getSeconds());
    }
}