package com.project.llmservice.integration;

import com.project.llmservice.provider.OllamaProvider;
import com.project.llmservice.exception.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LlmAnalysisFailureApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OllamaProvider ollamaProvider;

    @Test
    void shouldReturnBadGatewayWhenLlmProviderFails() throws Exception {

        when(ollamaProvider.generate(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new ProviderException("LLM provider unavailable"));

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
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code")
                        .value("LLM_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("LLM provider unavailable"))
                .andExpect(jsonPath("$.timestamp")
                        .isNotEmpty());
    }
}