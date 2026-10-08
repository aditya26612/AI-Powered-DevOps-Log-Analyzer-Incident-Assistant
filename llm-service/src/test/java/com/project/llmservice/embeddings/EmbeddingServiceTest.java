package com.project.llmservice.embeddings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    private EmbeddingModelProvider provider;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        provider = mock(EmbeddingModelProvider.class);
        embeddingService = new EmbeddingService(provider);
    }

    @Test
    void shouldGenerateEmbedding() {

        String text = "Database connection failed";

        float[] expectedEmbedding = {
                0.1f,
                0.2f,
                0.3f
        };

        when(provider.embed(text))
                .thenReturn(expectedEmbedding);

        float[] result = embeddingService.embed(text);

        assertArrayEquals(
                expectedEmbedding,
                result
        );

        verify(provider).embed(text);
    }

    @Test
    void shouldRejectNullText() {

        assertThrows(
                IllegalArgumentException.class,
                () -> embeddingService.embed(null)
        );
    }

    @Test
    void shouldRejectBlankText() {

        assertThrows(
                IllegalArgumentException.class,
                () -> embeddingService.embed("   ")
        );
    }
}