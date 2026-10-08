package com.project.llmservice.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LlmHealthActuatorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderHealthChecker providerHealthChecker;

    @Test
    void shouldReportLlmHealthAsUp() throws Exception {

        when(providerHealthChecker.isHealthy())
                .thenReturn(true);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.llm.status").value("UP"))
                .andExpect(jsonPath("$.components.llm.details.provider")
                        .value("OLLAMA"));
    }

    @Test
    void shouldReportLlmHealthAsDown() throws Exception {

        when(providerHealthChecker.isHealthy())
                .thenReturn(false);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.llm.status").value("DOWN"))
                .andExpect(jsonPath("$.components.llm.details.provider")
                        .value("OLLAMA"))
                .andExpect(jsonPath("$.components.llm.details.reason")
                        .value("LLM provider is unavailable"));
    }
}