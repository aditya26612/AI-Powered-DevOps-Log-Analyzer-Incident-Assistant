package com.project.llmservice.embeddings.impl;

import com.project.llmservice.embeddings.EmbeddingModelProvider;
import com.project.llmservice.exception.ProviderException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class OllamaEmbeddingModelProvider implements EmbeddingModelProvider {

    private final EmbeddingModel embeddingModel;

    public OllamaEmbeddingModelProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception ex) {
            throw new ProviderException(
                    "Failed to generate embedding using Ollama",
                    ex
            );
        }
    }
}