package com.project.llmservice.service.impl;

import com.project.llmservice.dto.response.HealthResponse;
import com.project.llmservice.properties.ApplicationProperties;
import com.project.llmservice.properties.OllamaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthServiceImplTest {

    private ApplicationProperties applicationProperties;
    private OllamaProperties ollamaProperties;

    private HealthServiceImpl healthService;

    @BeforeEach
    void setUp() {

        applicationProperties =
                new ApplicationProperties();

        applicationProperties.setProvider("OLLAMA");

        ollamaProperties =
                new OllamaProperties();

        ollamaProperties.getChat()
                .getOptions()
                .setModel("llama2:latest");

        healthService =
                new HealthServiceImpl(
                        applicationProperties,
                        ollamaProperties
                );
    }

    @Test
    void shouldReturnHealthyResponse() {

        HealthResponse response =
                healthService.getHealth();

        assertNotNull(response);

        assertEquals("UP", response.getStatus());
        assertEquals(
                "DevInsight LLM Service",
                response.getService()
        );
        assertEquals("1.0.0", response.getVersion());
        assertEquals("OLLAMA", response.getProvider());
        assertEquals(
                "llama2:latest",
                response.getModel()
        );

        assertFalse(response.isModelLoaded());
        assertNotNull(response.getTimestamp());
    }
}