package com.project.llmservice.controller;

import com.project.llmservice.service.TestLlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestController.class)
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestLlmService testLlmService;

    @Test
    void shouldReturnLlmConnectionResult() throws Exception {

        when(testLlmService.testConnection())
                .thenReturn("READY");

        mockMvc.perform(
                        get("/test")
                )
                .andExpect(status().isOk())
                .andExpect(content().string("READY"));
    }
}