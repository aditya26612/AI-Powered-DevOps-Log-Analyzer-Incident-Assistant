
package com.project.llmservice.rag;

import com.project.llmservice.rag.bm25.BM25Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class HybridRetriever implements Retriever {

    private static final int RRF_K = 60;

    private static final String SOURCE_DOCUMENT_ID =
            "sourceDocumentId";

    private final VectorRetriever vectorRetriever;
    private final BM25Retriever bm25Retriever;

    private List<RetrievalResult> retrieveSafely(
            String retrieverName,
            Supplier<List<RetrievalResult>> retrievalOperation
    ) {
        try {
            return retrievalOperation.get();

        } catch (RuntimeException exception) {

            log.warn(
                    "{} retrieval failed. Continuing with available retrieval results.",
                    retrieverName,
                    exception
            );

            return List.of();
        }
    }

    /*
     * Resolve the original source document ID.
     *
     * Primary source:
     *   metadata["sourceDocumentId"]
     *
     * Fallback:
     *   Remove "-chunk-N" and ".md" from the chunk ID.
     */
    private String resolveSourceDocumentId(
            KnowledgeDocument document
    ) {

        if (document.getMetadata() != null) {

            String sourceId =
                    document.getMetadata().get(SOURCE_DOCUMENT_ID);

            if (sourceId != null && !sourceId.isBlank()) {
                return normalizeDocumentId(sourceId);
            }
        }

        return normalizeDocumentId(document.getId());
    }

    private String normalizeDocumentId(String id) {

        if (id == null || id.isBlank()) {
            return null;
        }

        return id
                .replaceFirst("-chunk-\\d+$", "")
                .replaceFirst("\\.md$", "");
    }

    /*
     * Convert chunk-level results into source-document-level
     * rankings for one retriever.
     *
     * The first occurrence of a source document represents its
     * best-ranked chunk. Further chunks from that source document
     * are ignored for this retriever.
     */
    private List<RankedDocument> aggregateBySourceDocument(
            String retrieverName,
            List<RetrievalResult> results
    ) {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<RankedDocument> aggregated = new ArrayList<>();

        Set<String> seenSourceIds = new HashSet<>();

        int documentRank = 0;

        for (RetrievalResult result : results) {

            if (result == null || result.getDocument() == null) {
                log.warn(
                        "{} returned a null result or document. Skipping.",
                        retrieverName
                );
                continue;
            }

            KnowledgeDocument document = result.getDocument();

            String sourceId = resolveSourceDocumentId(document);

            if (sourceId == null || sourceId.isBlank()) {
                log.warn(
                        "{} returned a document with no valid source ID. Skipping.",
                        retrieverName
                );
                continue;
            }

            if (!seenSourceIds.add(sourceId)) {
                log.debug(
                        "{} returned another chunk for source '{}'. Skipping duplicate.",
                        retrieverName,
                        sourceId
                );
                continue;
            }

            documentRank++;

            aggregated.add(
                    new RankedDocument(
                            sourceId,
                            document,
                            documentRank,
                            result.getScore()
                    )
            );
        }

        return aggregated;
    }

    /*
     * Convert internal source-document rankings into
     * externally accessible diagnostic rankings.
     */
    private List<SourceDocumentRanking> toSourceDocumentRankings(
            List<RankedDocument> rankedDocuments
    ) {

        return rankedDocuments.stream()
                .map(document ->
                        new SourceDocumentRanking(
                                document.sourceId(),
                                document.document().getId(),
                                document.rank(),
                                document.retrievalScore()
                        )
                )
                .toList();
    }

    /*
     * Add source-document-level rankings to the RRF map.
     *
     * retrieverPriority:
     *   0 = Dense
     *   1 = BM25
     */
    private void addResults(
            Map<String, HybridEntry> fusedResults,
            List<RankedDocument> rankedDocuments,
            int retrieverPriority
    ) {

        for (RankedDocument rankedDocument : rankedDocuments) {

            String sourceId = rankedDocument.sourceId();

            double rrfScore =
                    1.0 / (RRF_K + rankedDocument.rank());

            HybridEntry existing = fusedResults.get(sourceId);

            if (existing == null) {

                Integer denseRank =
                        retrieverPriority == 0
                                ? rankedDocument.rank()
                                : null;

                Integer bm25Rank =
                        retrieverPriority == 1
                                ? rankedDocument.rank()
                                : null;

                double denseContribution =
                        retrieverPriority == 0 ? rrfScore : 0.0;

                double bm25Contribution =
                        retrieverPriority == 1 ? rrfScore : 0.0;

                fusedResults.put(
                        sourceId,
                        new HybridEntry(
                                sourceId,
                                rankedDocument.document(),
                                rrfScore,
                                rankedDocument.rank(),
                                retrieverPriority,
                                denseRank,
                                bm25Rank,
                                denseContribution,
                                bm25Contribution
                        )
                );

                continue;
            }

            /*
             * Select a deterministic representative chunk:
             * 1. Prefer the chunk with the better individual rank.
             * 2. If ranks tie, prefer Dense (priority 0).
             * 3. If still tied, use lexicographically smaller chunk ID.
             */
            boolean shouldReplaceRepresentative =
                    rankedDocument.rank() < existing.bestRank()
                            || (
                            rankedDocument.rank() == existing.bestRank()
                                    && retrieverPriority < existing.retrieverPriority()
                    )
                            || (
                            rankedDocument.rank() == existing.bestRank()
                                    && retrieverPriority == existing.retrieverPriority()
                                    && rankedDocument.document().getId()
                                    .compareTo(existing.document().getId()) < 0
                    );

            KnowledgeDocument representative =
                    shouldReplaceRepresentative
                            ? rankedDocument.document()
                            : existing.document();

            int bestRank =
                    shouldReplaceRepresentative
                            ? rankedDocument.rank()
                            : existing.bestRank();

            int representativePriority =
                    shouldReplaceRepresentative
                            ? retrieverPriority
                            : existing.retrieverPriority();

            Integer denseRank =
                    retrieverPriority == 0
                            ? rankedDocument.rank()
                            : existing.denseRank();

            Integer bm25Rank =
                    retrieverPriority == 1
                            ? rankedDocument.rank()
                            : existing.bm25Rank();

            double denseContribution =
                    existing.denseContribution()
                            + (retrieverPriority == 0 ? rrfScore : 0.0);

            double bm25Contribution =
                    existing.bm25Contribution()
                            + (retrieverPriority == 1 ? rrfScore : 0.0);

            fusedResults.put(
                    sourceId,
                    new HybridEntry(
                            sourceId,
                            representative,
                            existing.score() + rrfScore,
                            bestRank,
                            representativePriority,
                            denseRank,
                            bm25Rank,
                            denseContribution,
                            bm25Contribution
                    )
            );
        }
    }

    /*
     * Existing Retriever API remains unchanged.
     */
    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {

        return retrieveWithDiagnostics(query, topK).results();
    }

    /*
     * Executes Dense + BM25 + RRF and exposes the internal
     * rankings and score contributions for diagnostics.
     *
     * The actual retrieval and ranking algorithm is unchanged.
     */
    public HybridRetrievalDiagnostics retrieveWithDiagnostics(
            String query,
            int topK
    ) {

        if (query == null || query.isBlank() || topK <= 0) {

            return new HybridRetrievalDiagnostics(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        /*
         * Both component retrievers receive exactly the same
         * candidate depth supplied by the caller.
         */
        int candidateK = topK;

        List<RetrievalResult> denseResults =
                retrieveSafely(
                        "Dense",
                        () -> vectorRetriever.retrieve(query, candidateK)
                );

        List<RetrievalResult> bm25Results =
                retrieveSafely(
                        "BM25",
                        () -> bm25Retriever.retrieve(query, candidateK)
                );

        /*
         * Stage 1:
         * Aggregate chunks into source-document rankings.
         */
        List<RankedDocument> denseDocuments =
                aggregateBySourceDocument(
                        "Dense",
                        denseResults
                );

        List<RankedDocument> bm25Documents =
                aggregateBySourceDocument(
                        "BM25",
                        bm25Results
                );

        /*
         * Preserve the individual source-document rankings
         * before fusion.
         */
        List<SourceDocumentRanking> denseRanking =
                toSourceDocumentRankings(denseDocuments);

        List<SourceDocumentRanking> bm25Ranking =
                toSourceDocumentRankings(bm25Documents);

        /*
         * Stage 2:
         * Apply RRF using source-document ranks.
         */
        Map<String, HybridEntry> fusedResults = new HashMap<>();

        addResults(
                fusedResults,
                denseDocuments,
                0
        );

        addResults(
                fusedResults,
                bm25Documents,
                1
        );

        /*
         * Stage 3:
         * Sort deterministically.
         *
         * Do not limit this list yet. We need every fused
         * candidate for complete diagnostic information.
         */
        List<HybridEntry> sortedEntries =
                fusedResults.values()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(HybridEntry::score)
                                        .reversed()
                                        .thenComparing(HybridEntry::sourceId)
                        )
                        .toList();

        /*
         * Create the normal Hybrid retrieval response.
         * This still returns only topK results.
         */
        List<RetrievalResult> finalResults =
                sortedEntries.stream()
                        .limit(topK)
                        .map(entry ->
                                RetrievalResult.builder()
                                        .document(entry.document())
                                        .score(entry.score())
                                        .build()
                        )
                        .toList();

        /*
         * Create diagnostics for the entire fused candidate set.
         * Hybrid ranks are assigned before topK truncation.
         */
        List<HybridDocumentDiagnostic> fusionDiagnostics =
                new ArrayList<>();

        for (int i = 0; i < sortedEntries.size(); i++) {

            HybridEntry entry = sortedEntries.get(i);

            fusionDiagnostics.add(
                    new HybridDocumentDiagnostic(
                            entry.sourceId(),
                            entry.document().getId(),
                            entry.denseRank(),
                            entry.bm25Rank(),
                            entry.denseContribution(),
                            entry.bm25Contribution(),
                            entry.score(),
                            i + 1
                    )
            );
        }

        return new HybridRetrievalDiagnostics(
                finalResults,
                denseRanking,
                bm25Ranking,
                List.copyOf(fusionDiagnostics)
        );
    }

    /*
     * Internal records.
     */
    private record RankedDocument(
            String sourceId,
            KnowledgeDocument document,
            int rank,
            double retrievalScore
    ) {}

    private record HybridEntry(
            String sourceId,
            KnowledgeDocument document,
            double score,
            int bestRank,
            int retrieverPriority,
            Integer denseRank,
            Integer bm25Rank,
            double denseContribution,
            double bm25Contribution
    ) {}

    /*
     * Public diagnostic records.
     */

    public record SourceDocumentRanking(
            String documentId,
            String chunkId,
            int rank,
            double retrievalScore
    ) {}

    public record HybridDocumentDiagnostic(
            String documentId,
            String representativeChunkId,
            Integer denseRank,
            Integer bm25Rank,
            double denseContribution,
            double bm25Contribution,
            double finalRrfScore,
            int hybridRank
    ) {}

    public record HybridRetrievalDiagnostics(
            List<RetrievalResult> results,
            List<SourceDocumentRanking> denseRanking,
            List<SourceDocumentRanking> bm25Ranking,
            List<HybridDocumentDiagnostic> fusedRanking
    ) {

        public HybridRetrievalDiagnostics {

            results = List.copyOf(results);
            denseRanking = List.copyOf(denseRanking);
            bm25Ranking = List.copyOf(bm25Ranking);
            fusedRanking = List.copyOf(fusedRanking);
        }
    }
}
