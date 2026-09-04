package com.project.llmservice.rag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SimpleRetriever implements Retriever {

    private final DocumentLoader documentLoader;

    public SimpleRetriever(
            @Qualifier("resourceDocumentLoader")
            DocumentLoader documentLoader
    ) {
        this.documentLoader = documentLoader;
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        String normalizedQuery = query.toLowerCase();

        return documentLoader.load()
                .stream()
                .map(document -> {

                    String content =
                            document.getContent().toLowerCase();

                    double score =
                            calculateScore(
                                    normalizedQuery,
                                    content
                            );

                    return RetrievalResult.builder()
                            .document(document)
                            .score(score)
                            .build();
                })
                .filter(result -> result.getScore() > 0)
                .sorted(
                        Comparator.comparingDouble(
                                RetrievalResult::getScore
                        ).reversed()
                )
                .limit(topK)
                .toList();
    }

    private double calculateScore(
            String query,
            String content
    ) {

        String[] terms = query.split("\\s+");

        if (terms.length == 0) {
            return 0;
        }

        long matches = 0;

        for (String term : terms) {

            if (!term.isBlank() && content.contains(term)) {
                matches++;
            }
        }

        return (double) matches / terms.length;
    }
}