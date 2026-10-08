package com.project.llmservice.embeddings;

public interface EmbeddingModelProvider {

    float[] embed(String text);
}