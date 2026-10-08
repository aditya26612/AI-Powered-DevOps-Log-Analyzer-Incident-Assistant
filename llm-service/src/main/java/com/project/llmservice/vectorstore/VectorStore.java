package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;

import java.util.List;

public interface VectorStore {

    void add(
            KnowledgeDocument document,
            float[] embedding
    );

    List<RetrievalResult> search(
            float[] queryEmbedding,
            int topK
    );
}