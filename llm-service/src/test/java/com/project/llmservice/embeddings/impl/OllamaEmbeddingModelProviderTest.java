package com.project.llmservice.embeddings.impl;

import com.project.llmservice.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaEmbeddingModelProviderTest {

    private EmbeddingModel embeddingModel;
    private OllamaEmbeddingModelProvider provider;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        provider = new OllamaEmbeddingModelProvider(embeddingModel);
    }

    @Test
    void shouldGenerateEmbeddingSuccessfully() {

        float[] expectedEmbedding = {
                0.1f,
                0.2f,
                0.3f
        };

        when(embeddingModel.embed("Database connection failed"))
                .thenReturn(expectedEmbedding);

        float[] result =
                provider.embed("Database connection failed");

        assertNotNull(result);
        assertArrayEquals(expectedEmbedding, result);

        verify(embeddingModel)
                .embed("Database connection failed");
    }

    @Test
    void shouldWrapProviderExceptionWhenEmbeddingFails() {

        when(embeddingModel.embed("Database connection failed"))
                .thenThrow(new RuntimeException("Ollama unavailable"));

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> provider.embed("Database connection failed")
        );

        assertEquals(
                "Failed to generate embedding using Ollama",
                exception.getMessage()
        );

        verify(embeddingModel)
                .embed("Database connection failed");
    }
}