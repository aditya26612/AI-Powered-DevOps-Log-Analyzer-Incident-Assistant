package com.project.llmservice.rag.reranker;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LexicalReranker implements Reranker {

    @Override
    public List<RetrievalResult> rerank(
            String query,
            List<RetrievalResult> candidates,
            int topK
    ) {

        if (query == null
                || query.isBlank()
                || candidates == null
                || candidates.isEmpty()
                || topK <= 0) {

            return List.of();
        }

        Set<String> queryTerms = tokenize(query);

        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<Set<String>> documentTerms =
                candidates.stream()
                        .map(result ->
                                tokenize(
                                        result.getDocument()
                                                .getContent()
                                )
                        )
                        .toList();

        int documentCount = documentTerms.size();

        Map<String, Long> documentFrequency =
                documentTerms.stream()
                        .flatMap(Set::stream)
                        .collect(
                                Collectors.groupingBy(
                                        Function.identity(),
                                        Collectors.counting()
                                )
                        );

        return candidates.stream()
                .map(result -> {

                    Set<String> terms =
                            tokenize(
                                    result.getDocument()
                                            .getContent()
                            );

                    double score = 0.0;

                    for (String queryTerm : queryTerms) {

                        if (!terms.contains(queryTerm)) {
                            continue;
                        }

                        long df =
                                documentFrequency.getOrDefault(
                                        queryTerm,
                                        0L
                                );

                        double idf =
                                Math.log(
                                        (double) (documentCount + 1)
                                                / (df + 1)
                                ) + 1.0;

                        score += idf;
                    }

                    return RetrievalResult.builder()
                            .document(result.getDocument())
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

    private Set<String> tokenize(String text) {

        return Arrays.stream(
                        text.toLowerCase()
                                .split("[^a-z0-9]+")
                )
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}