package com.project.llmservice.vectorstore;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public void addDocument(KnowledgeDocument document) {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (document.getContent() == null
                || document.getContent().isBlank()) {

            throw new IllegalArgumentException(
                    "Document content must not be null or blank"
            );
        }

        float[] embedding =
                embeddingService.embed(
                        document.getContent()
                );

        vectorStore.add(
                document,
                embedding
        );
    }

    public List<RetrievalResult> search(
            String query,
            int topK
    ) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        float[] queryEmbedding =
                embeddingService.embed(query);

        return vectorStore.search(
                queryEmbedding,
                topK
        );
    }
}