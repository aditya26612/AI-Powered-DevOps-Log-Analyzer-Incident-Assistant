package com.project.llmservice.dto.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmAnalysisResponseTest {

    @Test
    void shouldBuildResponseSuccessfully() {

        LlmAnalysisResponse response =
                LlmAnalysisResponse.builder()
                        .summary("Database connection failed.")
                        .rootCause("PostgreSQL is unreachable.")
                        .severity("CRITICAL")
                        .recommendation(
                                "Verify database availability."
                        )
                        .build();

        assertEquals(
                "Database connection failed.",
                response.getSummary()
        );
        assertEquals(
                "PostgreSQL is unreachable.",
                response.getRootCause()
        );
        assertEquals(
                "CRITICAL",
                response.getSeverity()
        );
        assertEquals(
                "Verify database availability.",
                response.getRecommendation()
        );
    }
}