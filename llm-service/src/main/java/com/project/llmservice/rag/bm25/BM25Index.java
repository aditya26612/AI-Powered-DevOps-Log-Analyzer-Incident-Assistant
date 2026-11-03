
package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BM25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final AtomicReference<IndexState> activeState =
            new AtomicReference<>(IndexState.empty());

    /*
     * STEP 1: Prepare a replacement without publishing it.
     */
    public PreparedIndex prepare(
            List<KnowledgeDocument> newDocuments
    ) {

        if (newDocuments == null) {
            throw new IllegalArgumentException(
                    "Documents must not be null"
            );
        }

        IndexState candidateState = buildState(newDocuments);

        return new PreparedIndex(candidateState);
    }

    /*
     * STEP 2: Publish a previously prepared replacement.
     */
    public void publish(PreparedIndex preparedIndex) {

        if (preparedIndex == null) {
            throw new IllegalArgumentException(
                    "Prepared index must not be null"
            );
        }

        activeState.set(preparedIndex.state());
    }

    /*
     * Compatibility method for existing callers.
     */
    public synchronized void rebuild(
            List<KnowledgeDocument> newDocuments
    ) {

        PreparedIndex preparedIndex = prepare(newDocuments);

        publish(preparedIndex);
    }

    /*
     * Retained for compatibility with existing callers.
     */
    public synchronized void addDocument(
            KnowledgeDocument document
    ) {

        validateDocument(document);

        IndexState currentState = activeState.get();

        List<KnowledgeDocument> updatedDocuments =
                new ArrayList<>();

        for (IndexedDocument indexedDocument :
                currentState.documents()) {

            updatedDocuments.add(indexedDocument.document());
        }

        updatedDocuments.add(document);

        PreparedIndex preparedIndex = prepare(updatedDocuments);

        publish(preparedIndex);
    }

    public List<RetrievalResult> search(
            String query,
            int topK
    ) {

        if (query == null
                || query.isBlank()
                || topK <= 0) {

            return List.of();
        }

        IndexState state = activeState.get();

        if (state.documents().isEmpty()
                || state.averageDocumentLength() == 0.0) {

            return List.of();
        }

        List<String> queryTokens = tokenize(query);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<RetrievalResult> results = new ArrayList<>();

        for (IndexedDocument indexedDocument :
                state.documents()) {

            double score = calculateScore(
                    queryTokens,
                    indexedDocument,
                    state
            );

            if (score <= 0.0) {
                continue;
            }

            results.add(
                    RetrievalResult.builder()
                            .document(indexedDocument.document())
                            .score(score)
                            .build()
            );
        }

        return results.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        RetrievalResult::getScore
                                ).reversed()
                                .thenComparing(
                                        result -> result
                                                .getDocument()
                                                .getId()
                                )
                )
                .limit(topK)
                .toList();
    }


    private IndexState buildState(
            List<KnowledgeDocument> sourceDocuments
    ) {

        List<IndexedDocument> newIndexedDocuments =
                new ArrayList<>();

        Map<String, Integer> newDocumentFrequency =
                new HashMap<>();

        // Track document IDs to prevent duplicate entries.
        Set<String> documentIds = new HashSet<>();

        int totalDocumentLength = 0;

        for (KnowledgeDocument document : sourceDocuments) {

            validateDocument(document);

            // Reject duplicate document IDs.
            if (!documentIds.add(document.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate document ID: " + document.getId()
                );
            }

            List<String> tokens =
                    tokenize(document.getContent());

            if (tokens.isEmpty()) {
                throw new IllegalArgumentException(
                        "Document must contain searchable tokens"
                );
            }

            Map<String, Integer> termFrequency =
                    buildTermFrequency(tokens);

            IndexedDocument indexedDocument =
                    new IndexedDocument(
                            document,
                            Map.copyOf(termFrequency),
                            tokens.size()
                    );

            newIndexedDocuments.add(indexedDocument);

            Set<String> uniqueTerms =
                    new HashSet<>(tokens);

            for (String term : uniqueTerms) {

                newDocumentFrequency.merge(
                        term,
                        1,
                        Integer::sum
                );
            }

            totalDocumentLength += tokens.size();
        }

        double averageDocumentLength =
                newIndexedDocuments.isEmpty()
                        ? 0.0
                        : (double) totalDocumentLength
                        / newIndexedDocuments.size();

        return new IndexState(
                List.copyOf(newIndexedDocuments),
                Map.copyOf(newDocumentFrequency),
                averageDocumentLength
        );
    }


    private void validateDocument(
            KnowledgeDocument document
    ) {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (document.getId() == null
                || document.getId().isBlank()) {

            throw new IllegalArgumentException(
                    "Document ID must not be blank"
            );
        }

        if (document.getContent() == null
                || document.getContent().isBlank()) {

            throw new IllegalArgumentException(
                    "Document content must not be null or blank"
            );
        }
    }



    private double calculateScore(
            List<String> queryTokens,
            IndexedDocument document,
            IndexState state
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
                    state.documentFrequency()
                            .getOrDefault(term, 0);

            double idf =
                    Math.log(
                            1.0
                                    + (
                                    (state.documents().size()
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
                                    / state.averageDocumentLength()
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

        Map<String, Integer> frequency = new HashMap<>();

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
                text.toLowerCase(Locale.ROOT);

        String[] rawTokens =
                normalized.split("[^a-z0-9]+");

        List<String> tokens = new ArrayList<>();

        for (String token : rawTokens) {

            if (!token.isBlank()) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    /*
     * A prepared candidate index.
     *
     * It cannot be modified after construction.
     */
    public static final class PreparedIndex {

        private final IndexState state;

        private PreparedIndex(IndexState state) {
            this.state = state;
        }

        private IndexState state() {
            return state;
        }

        public int size() {
            return state.documents().size();
        }
    }

    private record IndexState(
            List<IndexedDocument> documents,
            Map<String, Integer> documentFrequency,
            double averageDocumentLength
    ) {

        private static IndexState empty() {

            return new IndexState(
                    List.of(),
                    Map.of(),
                    0.0
            );
        }
    }

    private record IndexedDocument(
            KnowledgeDocument document,
            Map<String, Integer> termFrequency,
            int documentLength
    ) {
    }
}
