package com.project.llmservice.dto.response;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HealthResponseTest {

    @Test
    void shouldBuildHealthResponseSuccessfully() {

        LocalDateTime timestamp =
                LocalDateTime.now();

        HealthResponse response =
                HealthResponse.builder()
                        .status("UP")
                        .service("DevInsight LLM Service")
                        .version("1.0.0")
                        .provider("OLLAMA")
                        .model("llama2:latest")
                        .modelLoaded(false)
                        .timestamp(timestamp)
                        .build();

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
        assertEquals(timestamp, response.getTimestamp());
    }
}