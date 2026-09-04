package com.project.llmservice.service.impl;

import com.project.llmservice.provider.LlmProvider;
import com.project.llmservice.provider.ProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class TestLlmServiceImplTest {

    private ProviderFactory providerFactory;
    private LlmProvider llmProvider;

    private TestLlmServiceImpl testLlmService;

    @BeforeEach
    void setUp() {

        providerFactory =
                mock(ProviderFactory.class);

        llmProvider =
                mock(LlmProvider.class);

        testLlmService =
                new TestLlmServiceImpl(providerFactory);
    }

    @Test
    void shouldTestLlmConnectionSuccessfully() {

        when(providerFactory.getProvider())
                .thenReturn(llmProvider);

        when(llmProvider.generate(
                "Say only the word READY."
        )).thenReturn("READY");

        String response =
                testLlmService.testConnection();

        assertNotNull(response);
        assertEquals("READY", response);

        verify(providerFactory)
                .getProvider();

        verify(llmProvider)
                .generate("Say only the word READY.");
    }
}