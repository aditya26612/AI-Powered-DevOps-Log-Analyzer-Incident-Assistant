package com.project.llmservice.provider.impl;

import com.project.llmservice.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OllamaProviderImplTest {

    private ChatClient chatClient;

    private ChatClient.ChatClientRequestSpec requestSpec;

    private ChatClient.CallResponseSpec responseSpec;

    private OllamaProviderImpl provider;

    @BeforeEach
    void setUp() {

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);

        provider = new OllamaProviderImpl(chatClient);
    }

    @Test
    void shouldGenerateResponseSuccessfully() {

        String prompt = "Analyze this log";
        String expectedResponse = "Database connection failed.";

        when(chatClient.prompt(prompt))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenReturn(responseSpec);

        when(responseSpec.content())
                .thenReturn(expectedResponse);

        String actualResponse = provider.generate(prompt);

        assertEquals(expectedResponse, actualResponse);

        verify(chatClient).prompt(prompt);
        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    void shouldThrowProviderExceptionWhenPromptIsNull() {

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> provider.generate(null)
        );

        assertEquals(
                "Prompt must not be null or empty",
                exception.getMessage()
        );

        verifyNoInteractions(chatClient);
    }

    @Test
    void shouldThrowProviderExceptionWhenPromptIsBlank() {

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> provider.generate("   ")
        );

        assertEquals(
                "Prompt must not be null or empty",
                exception.getMessage()
        );

        verifyNoInteractions(chatClient);
    }

    @Test
    void shouldThrowProviderExceptionWhenOllamaFails() {

        String prompt = "Analyze this log";

        when(chatClient.prompt(prompt))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenThrow(new RuntimeException("Connection refused"));

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> provider.generate(prompt)
        );

        assertEquals(
                "Failed to communicate with Ollama provider",
                exception.getMessage()
        );

        assertEquals(
                "Connection refused",
                exception.getCause().getMessage()
        );
    }

    @Test
    void shouldThrowProviderExceptionWhenResponseIsEmpty() {

        String prompt = "Analyze this log";

        when(chatClient.prompt(prompt))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenReturn(responseSpec);

        when(responseSpec.content())
                .thenReturn("");

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> provider.generate(prompt)
        );

        assertEquals(
                "LLM provider returned an empty response",
                exception.getMessage()
        );
    }
}