package com.project.llmservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.service.LlmAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.project.llmservice.exception.ProviderException;
import com.project.llmservice.exception.ResponseParsingException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LlmAnalysisController.class)
class LlmAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LlmAnalysisService llmAnalysisService;

    @Test
    void shouldAnalyzeLogSuccessfully() throws Exception {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T18:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        LlmAnalysisResponse response = LlmAnalysisResponse.builder()
                .summary("Database connection failed.")
                .rootCause("PostgreSQL database is unreachable.")
                .severity("CRITICAL")
                .recommendation("Verify database availability.")
                .build();

        when(llmAnalysisService.analyze(any(LlmAnalysisRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary")
                        .value("Database connection failed."))
                .andExpect(jsonPath("$.rootCause")
                        .value("PostgreSQL database is unreachable."))
                .andExpect(jsonPath("$.severity")
                        .value("CRITICAL"))
                .andExpect(jsonPath("$.recommendation")
                        .value("Verify database availability."));

        verify(llmAnalysisService).analyze(any(LlmAnalysisRequest.class));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {

        String invalidRequest = """
            {
                "timestamp": "",
                "level": "ERROR",
                "serviceName": "",
                "message": ""
            }
            """;

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleProviderException() throws Exception {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T18:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        when(llmAnalysisService.analyze(any(LlmAnalysisRequest.class)))
                .thenThrow(new ProviderException("Ollama provider is unavailable"));

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value("LLM_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Ollama provider is unavailable"))
                .andExpect(jsonPath("$.timestamp")
                        .exists());

        verify(llmAnalysisService).analyze(any(LlmAnalysisRequest.class));
    }

    @Test
    void shouldHandleResponseParsingException() throws Exception {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T18:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        when(llmAnalysisService.analyze(any(LlmAnalysisRequest.class)))
                .thenThrow(new ResponseParsingException(
                        "Failed to parse LLM response as JSON"
                ));

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value("RESPONSE_PARSING_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Failed to parse LLM response as JSON"))
                .andExpect(jsonPath("$.timestamp")
                        .exists());

        verify(llmAnalysisService).analyze(any(LlmAnalysisRequest.class));
    }

}