package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;

public record EmbeddedDocument(
        KnowledgeDocument document,
        float[] embedding
) {

    public EmbeddedDocument {
        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException(
                    "Embedding must not be null or empty"
            );
        }

        // Prevent callers from modifying the stored embedding.
        embedding = embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}