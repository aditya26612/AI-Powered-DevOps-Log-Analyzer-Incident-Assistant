package com.project.llmservice.integration;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.service.LlmAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class LlmAnalysisOllamaIntegrationTest {

    @Autowired
    private LlmAnalysisService llmAnalysisService;

    @Test
    void shouldPerformRealLlmAnalysis() {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T18:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        LlmAnalysisResponse response =
                llmAnalysisService.analyze(request);

        assertNotNull(response);

        assertFalse(
                response.getSummary() == null ||
                        response.getSummary().isBlank()
        );

        assertFalse(
                response.getRootCause() == null ||
                        response.getRootCause().isBlank()
        );

        assertFalse(
                response.getSeverity() == null ||
                        response.getSeverity().isBlank()
        );
    }
}