package com.project.llmservice.controller;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.service.LlmAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmAnalysisController.class)
class LlmAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmAnalysisService llmAnalysisService;

    @Test
    void shouldAnalyzeValidRequest() throws Exception {

        LlmAnalysisResponse response =
                LlmAnalysisResponse.builder()
                        .summary("Database connection failed.")
                        .rootCause("Database is unreachable.")
                        .severity("CRITICAL")
                        .recommendation("Check database availability.")
                        .build();

        when(llmAnalysisService.analyze(any(LlmAnalysisRequest.class)))
                .thenReturn(response);

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary")
                        .value("Database connection failed."))
                .andExpect(jsonPath("$.rootCause")
                        .value("Database is unreachable."))
                .andExpect(jsonPath("$.severity")
                        .value("CRITICAL"))
                .andExpect(jsonPath("$.recommendation")
                        .value("Check database availability."));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {

        String request = """
                {
                    "timestamp": "",
                    "level": "",
                    "serviceName": "",
                    "message": ""
                }
                """;

        mockMvc.perform(
                        post("/api/v1/llm/analyze")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }
}