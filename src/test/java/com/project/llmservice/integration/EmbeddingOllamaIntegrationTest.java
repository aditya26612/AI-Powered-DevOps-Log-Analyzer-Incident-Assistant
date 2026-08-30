package com.project.llmservice.integration;

import com.project.llmservice.embeddings.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmbeddingOllamaIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void shouldGenerateRealEmbeddingUsingOllama() {

        String text = "Database connection failed";

        float[] embedding = embeddingService.embed(text);

        assertNotNull(embedding);
        assertTrue(embedding.length > 0);
    }
}