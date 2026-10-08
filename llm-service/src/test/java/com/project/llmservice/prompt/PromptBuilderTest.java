package com.project.llmservice.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private PromptValidator promptValidator;

    @InjectMocks
    private PromptBuilder promptBuilder;

    @Test
    void shouldBuildPromptSuccessfully() {

        String template =
                "Timestamp: ${timestamp}\n" +
                        "Level: ${level}\n" +
                        "Service: ${serviceName}\n" +
                        "Message: ${message}";

        when(promptTemplateLoader.load(
                PromptType.ROOT_CAUSE_ANALYSIS
        )).thenReturn(template);

        PromptContext context = PromptContext.builder()
                .timestamp("2026-08-30T22:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .retrievedContext("No additional retrieved knowledge available.")
                .build();

        String result = promptBuilder.build(
                PromptType.ROOT_CAUSE_ANALYSIS,
                context
        );

        assertNotNull(result);

        assertTrue(result.contains(
                "2026-08-30T22:00:00"
        ));

        assertTrue(result.contains("ERROR"));

        assertTrue(result.contains(
                "payment-service"
        ));

        assertTrue(result.contains(
                "Database connection failed"
        ));

        verify(promptTemplateLoader)
                .load(PromptType.ROOT_CAUSE_ANALYSIS);

        verify(promptValidator)
                .validate(result);
    }
}