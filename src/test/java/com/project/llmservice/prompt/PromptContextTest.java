package com.project.llmservice.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptContextTest {

    @Test
    void shouldBuildPromptContext() {

        PromptContext context = PromptContext.builder()
                .timestamp("2026-08-30T22:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        assertNotNull(context);

        assertEquals(
                "2026-08-30T22:00:00",
                context.getTimestamp()
        );

        assertEquals(
                "ERROR",
                context.getLevel()
        );

        assertEquals(
                "payment-service",
                context.getServiceName()
        );

        assertEquals(
                "Database connection failed",
                context.getMessage()
        );
    }
}