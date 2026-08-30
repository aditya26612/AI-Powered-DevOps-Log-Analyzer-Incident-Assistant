package com.project.llmservice.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptIntegrationTest {

    private final PromptTemplateLoader loader =
            new PromptTemplateLoader();

    private final PromptValidator validator =
            new PromptValidator();

    private final PromptBuilder builder =
            new PromptBuilder(loader, validator);

    @Test
    void shouldBuildRealRootCauseAnalysisPrompt() {

        PromptContext context = PromptContext.builder()
                .timestamp("2026-08-30T22:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        String prompt = builder.build(
                PromptType.ROOT_CAUSE_ANALYSIS,
                context
        );

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());

        assertTrue(prompt.contains(
                "2026-08-30T22:00:00"
        ));

        assertTrue(prompt.contains("ERROR"));

        assertTrue(prompt.contains(
                "payment-service"
        ));

        assertTrue(prompt.contains(
                "Database connection failed"
        ));

        // Most important validation:
        // no ${...} placeholders should remain.
        assertFalse(prompt.matches("(?s).*\\$\\{[^}]+}.*"));
    }
}