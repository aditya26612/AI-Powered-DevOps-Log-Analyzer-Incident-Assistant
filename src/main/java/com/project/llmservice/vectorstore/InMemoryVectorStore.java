package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class InMemoryVectorStore implements VectorStore {

    private final List<VectorEntry> entries = new ArrayList<>();

    @Override
    public void add(
            KnowledgeDocument document,
            float[] embedding
    ) {

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

        entries.add(
                new VectorEntry(
                        document,
                        embedding
                )
        );
    }

    @Override
    public List<RetrievalResult> search(
            float[] queryEmbedding,
            int topK
    ) {

        if (queryEmbedding == null
                || queryEmbedding.length == 0
                || topK <= 0) {

            return List.of();
        }

        return entries.stream()
                .map(entry -> {

                    double score =
                            cosineSimilarity(
                                    queryEmbedding,
                                    entry.embedding()
                            );

                    return RetrievalResult.builder()
                            .document(entry.document())
                            .score(score)
                            .build();
                })
                .sorted(
                        Comparator.comparingDouble(
                                RetrievalResult::getScore
                        ).reversed()
                )
                .limit(topK)
                .toList();
    }

    private double cosineSimilarity(
            float[] vectorA,
            float[] vectorB
    ) {

        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must match"
            );
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {

            double a = vectorA[i];
            double b = vectorB[i];

            dotProduct += a * b;
            magnitudeA += a * a;
            magnitudeB += b * b;
        }

        if (magnitudeA == 0 || magnitudeB == 0) {
            return 0.0;
        }

        return dotProduct /
                (Math.sqrt(magnitudeA)
                        * Math.sqrt(magnitudeB));
    }

    private record VectorEntry(
            KnowledgeDocument document,
            float[] embedding
    ) {
    }
}