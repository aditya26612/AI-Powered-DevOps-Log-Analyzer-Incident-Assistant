package com.project.llmservice.dto.response;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    void shouldBuildErrorResponseSuccessfully() {

        LocalDateTime timestamp =
                LocalDateTime.now();

        ErrorResponse response =
                ErrorResponse.builder()
                        .code("LLM-001")
                        .message("Provider failed")
                        .timestamp(timestamp)
                        .build();

        assertEquals("LLM-001", response.getCode());
        assertEquals(
                "Provider failed",
                response.getMessage()
        );
        assertEquals(timestamp, response.getTimestamp());
    }
}