package com.project.llmservice.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaPropertiesTest {

    @Test
    void shouldSetOllamaProperties() {

        OllamaProperties properties =
                new OllamaProperties();

        properties.setBaseUrl("http://localhost:11434");

        properties.getChat()
                .getOptions()
                .setModel("llama2:latest");

        properties.getChat()
                .getOptions()
                .setTemperature(0.2);

        assertEquals(
                "http://localhost:11434",
                properties.getBaseUrl()
        );

        assertNotNull(properties.getChat());
        assertNotNull(properties.getChat().getOptions());

        assertEquals(
                "llama2:latest",
                properties.getChat()
                        .getOptions()
                        .getModel()
        );

        assertEquals(
                0.2,
                properties.getChat()
                        .getOptions()
                        .getTemperature()
        );
    }

    @Test
    void shouldInitializeNestedPropertiesByDefault() {

        OllamaProperties properties =
                new OllamaProperties();

        assertNotNull(properties.getChat());
        assertNotNull(properties.getChat().getOptions());
    }
}