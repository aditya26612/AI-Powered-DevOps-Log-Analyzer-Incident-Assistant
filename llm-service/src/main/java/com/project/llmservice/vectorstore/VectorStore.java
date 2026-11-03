package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;

import java.util.List;
import java.util.Set;

public interface VectorStore {

    void add(
            KnowledgeDocument document,
            float[] embedding
    );

    void synchronize(
            List<EmbeddedDocument> documents,
            Set<String> managedSourceDocumentIds
    );

    List<RetrievalResult> search(
            float[] queryEmbedding,
            int topK
    );
}