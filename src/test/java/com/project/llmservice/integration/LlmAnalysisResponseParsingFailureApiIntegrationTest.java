package com.project.llmservice.integration;

import com.project.llmservice.exception.ResponseParsingException;
import com.project.llmservice.parser.ResponseParser;
import com.project.llmservice.provider.OllamaProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LlmAnalysisResponseParsingFailureApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaProvider ollamaProvider;

    @MockitoBean
    private ResponseParser responseParser;

    @Test
    void shouldReturnInternalServerErrorWhenResponseParsingFails()
            throws Exception {

        when(ollamaProvider.generate(anyString()))
                .thenReturn("invalid llm response");

        when(responseParser.parse(anyString()))
                .thenThrow(
                        new ResponseParsingException(
                                "Failed to parse LLM response"
                        )
                );

        String request = """
                {
                    "timestamp": "2026-08-27T18:00:00",
                    "level": "ERROR",
                    "serviceName": "payment-service",
                    "message": "Database connection failed"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("RESPONSE_PARSING_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Failed to parse LLM response"))
                .andExpect(jsonPath("$.timestamp")
                        .isNotEmpty());
    }
}