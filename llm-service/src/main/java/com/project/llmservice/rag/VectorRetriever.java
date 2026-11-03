
package com.project.llmservice.rag;

import com.project.llmservice.properties.RagProperties;
import com.project.llmservice.vectorstore.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VectorRetriever implements Retriever {

    private final VectorStoreService vectorStoreService;
    private final RagProperties ragProperties;

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {

        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        return vectorStoreService.search(query, topK)
                .stream()
                .filter(result ->
                        result.getScore()
                                >= ragProperties.getSimilarityThreshold()
                )
                .toList();
    }
}
