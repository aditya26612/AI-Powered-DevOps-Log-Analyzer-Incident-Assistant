package com.project.llmservice.embeddings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModelProvider embeddingModelProvider;

    public float[] embed(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text cannot be null or blank"
            );
        }

        return embeddingModelProvider.embed(text);
    }
}