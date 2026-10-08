package com.project.llmservice.integration;

import com.project.llmservice.exception.RetrievalException;
import com.project.llmservice.rag.Retriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LlmAnalysisRetrievalFailureApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "hybridRetriever")
    private Retriever retriever;

    @Test
    void shouldReturnInternalServerErrorWhenRetrievalFails()
            throws Exception {

        when(retriever.retrieve(anyString(), anyInt()))
                .thenThrow(
                        new RetrievalException(
                                "Knowledge retrieval failed"
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
                        .value("RETRIEVAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Knowledge retrieval failed"))
                .andExpect(jsonPath("$.timestamp")
                        .isNotEmpty());
    }
}