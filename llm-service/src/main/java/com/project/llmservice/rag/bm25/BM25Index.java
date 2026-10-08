package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BM25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<IndexedDocument> documents = new ArrayList<>();

    private final Map<String, Integer> documentFrequency =
            new HashMap<>();

    private double averageDocumentLength = 0.0;

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

        List<String> tokens =
                tokenize(document.getContent());

        Map<String, Integer> termFrequency =
                buildTermFrequency(tokens);

        documents.add(
                new IndexedDocument(
                        document,
                        termFrequency,
                        tokens.size()
                )
        );

        Set<String> uniqueTerms =
                new HashSet<>(tokens);

        for (String term : uniqueTerms) {
            documentFrequency.merge(
                    term,
                    1,
                    Integer::sum
            );
        }

        averageDocumentLength =
                documents.stream()
                        .mapToInt(
                                IndexedDocument::documentLength
                        )
                        .average()
                        .orElse(0.0);
    }

    public List<RetrievalResult> search(
            String query,
            int topK
    ) {

        if (query == null
                || query.isBlank()
                || topK <= 0
                || documents.isEmpty()) {

            return List.of();
        }

        List<String> queryTokens =
                tokenize(query);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<RetrievalResult> results =
                new ArrayList<>();

        for (IndexedDocument indexedDocument : documents) {

            double score = calculateScore(
                    queryTokens,
                    indexedDocument
            );

            results.add(
                    RetrievalResult.builder()
                            .document(
                                    indexedDocument.document()
                            )
                            .score(score)
                            .build()
            );
        }

        return results.stream()
                .sorted(
                        Comparator.comparingDouble(
                                RetrievalResult::getScore
                        ).reversed()
                )
                .limit(topK)
                .toList();
    }

    private double calculateScore(
            List<String> queryTokens,
            IndexedDocument document
    ) {

        double score = 0.0;

        for (String term : queryTokens) {

            int termFrequency =
                    document.termFrequency()
                            .getOrDefault(term, 0);

            if (termFrequency == 0) {
                continue;
            }

            int documentFrequency =
                    this.documentFrequency
                            .getOrDefault(term, 0);

            double idf =
                    Math.log(
                            1.0
                                    + (
                                    (documents.size()
                                            - documentFrequency
                                            + 0.5)
                                            /
                                            (documentFrequency + 0.5)
                            )
                    );

            double lengthNormalization =
                    1.0
                            - B
                            + B
                            * (
                            document.documentLength()
                                    / averageDocumentLength
                    );

            double termScore =
                    idf
                            * (
                            (termFrequency * (K1 + 1.0))
                                    /
                                    (
                                            termFrequency
                                                    + K1
                                                    * lengthNormalization
                                    )
                    );

            score += termScore;
        }

        return score;
    }

    private Map<String, Integer> buildTermFrequency(
            List<String> tokens
    ) {

        Map<String, Integer> frequency =
                new HashMap<>();

        for (String token : tokens) {
            frequency.merge(
                    token,
                    1,
                    Integer::sum
            );
        }

        return frequency;
    }

    private List<String> tokenize(String text) {

        String normalized =
                text.toLowerCase();

        String[] rawTokens =
                normalized.split("[^a-z0-9]+");

        List<String> tokens =
                new ArrayList<>();

        for (String token : rawTokens) {

            if (!token.isBlank()) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private record IndexedDocument(
            KnowledgeDocument document,
            Map<String, Integer> termFrequency,
            int documentLength
    ) {
    }
}