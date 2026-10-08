package com.project.llmservice.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiConstantsTest {

    @Test
    void shouldContainCorrectApiPaths() {

        assertEquals(
                "/api/v1",
                ApiConstants.API_V1
        );

        assertEquals(
                "/api/v1/llm",
                ApiConstants.LLM
        );

        assertEquals(
                "/api/v1/llm/analyze",
                ApiConstants.LLM_ANALYZE
        );
    }
}