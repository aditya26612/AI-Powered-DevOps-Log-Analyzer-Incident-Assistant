package com.project.llmservice.rag;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.vectorstore.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VectorRetriever implements Retriever {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Override
    public List<RetrievalResult> retrieve(
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