package com.project.llmservice.rag;

import com.project.llmservice.rag.bm25.BM25Retriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HybridRetriever implements Retriever {

    private static final int RRF_K = 60;

    private final VectorRetriever vectorRetriever;
    private final BM25Retriever bm25Retriever;

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {
        if (query == null
                || query.isBlank()
                || topK <= 0) {
            return List.of();
        }

        int candidateK = Math.max(topK * 2, 10);

        List<RetrievalResult> denseResults =
                vectorRetriever.retrieve(query, candidateK);

        List<RetrievalResult> bm25Results =
                bm25Retriever.retrieve(query, candidateK);

        Map<String, HybridEntry> fusedResults =
                new HashMap<>();

        addResults(
                fusedResults,
                denseResults
        );

        addResults(
                fusedResults,
                bm25Results
        );

        List<RetrievalResult> candidates =
                fusedResults.values()
                        .stream()
                        .sorted(
                                (a, b) ->
                                        Double.compare(
                                                b.score(),
                                                a.score()
                                        )
                        )
                        .map(entry ->
                                RetrievalResult.builder()
                                        .document(entry.document())
                                        .score(entry.score())
                                        .build()
                        )
                        .toList();

        return candidates.stream()
                .limit(topK)
                .toList();
    }

    private void addResults(
            Map<String, HybridEntry> fusedResults,
            List<RetrievalResult> results
    ) {
        for (int i = 0; i < results.size(); i++) {

            RetrievalResult result = results.get(i);

            String documentId =
                    result.getDocument().getId();

            int rank = i + 1;

            double rrfScore =
                    1.0 / (RRF_K + rank);

            HybridEntry existing =
                    fusedResults.get(documentId);

            if (existing == null) {

                fusedResults.put(
                        documentId,
                        new HybridEntry(
                                result.getDocument(),
                                rrfScore
                        )
                );

            } else {

                fusedResults.put(
                        documentId,
                        new HybridEntry(
                                existing.document(),
                                existing.score()
                                        + rrfScore
                        )
                );
            }
        }
    }

    private record HybridEntry(
            KnowledgeDocument document,
            double score
    ) {
    }
}