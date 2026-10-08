package com.project.llmservice.mapper;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.prompt.PromptContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestMapperTest {

    private final RequestMapper requestMapper = new RequestMapper();

    @Test
    void shouldMapRequestToPromptContext() {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T18:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        PromptContext context =
                requestMapper.toPromptContext(request);

        assertNotNull(context);

        assertEquals(
                request.getTimestamp(),
                context.getTimestamp()
        );

        assertEquals(
                request.getLevel(),
                context.getLevel()
        );

        assertEquals(
                request.getServiceName(),
                context.getServiceName()
        );

        assertEquals(
                request.getMessage(),
                context.getMessage()
        );
    }
}