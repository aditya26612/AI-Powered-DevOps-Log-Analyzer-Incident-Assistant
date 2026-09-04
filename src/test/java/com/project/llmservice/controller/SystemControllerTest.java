package com.project.llmservice.controller;

import com.project.llmservice.dto.response.HealthResponse;
import com.project.llmservice.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    void shouldReturnHealthResponse() throws Exception {

        HealthResponse response =
                HealthResponse.builder()
                        .status("UP")
                        .service("DevInsight LLM Service")
                        .version("1.0.0")
                        .provider("OLLAMA")
                        .model("llama2:latest")
                        .modelLoaded(false)
                        .timestamp(LocalDateTime.of(
                                2026, 8, 27, 18, 0
                        ))
                        .build();

        when(healthService.getHealth())
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/system/health")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("UP"))
                .andExpect(jsonPath("$.service")
                        .value("DevInsight LLM Service"))
                .andExpect(jsonPath("$.version")
                        .value("1.0.0"))
                .andExpect(jsonPath("$.provider")
                        .value("OLLAMA"))
                .andExpect(jsonPath("$.model")
                        .value("llama2:latest"))
                .andExpect(jsonPath("$.modelLoaded")
                        .value(false));
    }
}