package com.project.llmservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.rag.HybridRetriever;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.ResourceDocumentLoader;
import com.project.llmservice.rag.bm25.BM25Retriever;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class HybridRetrievalBenchmarkTest {

    private static final int TOP_K = 5;
    private static final int CANDIDATE_K = 10;

    private static final String BENCHMARK_PATH =
            "retrieval/retrieval-benchmark-v2.json";

    @Autowired
    private ResourceDocumentLoader documentLoader;

    @Autowired
    private DocumentSplitter documentSplitter;
    @Autowired
    private BM25Retriever bm25Retriever;

    @Autowired
    private HybridRetriever hybridRetriever;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        List<KnowledgeDocument> documents =
                documentLoader.load();

        List<KnowledgeDocument> allChunks =
                new ArrayList<>();

        for (KnowledgeDocument document : documents) {
            allChunks.addAll(
                    documentSplitter.split(document)
            );
        }

        bm25Retriever.rebuildIndex(allChunks);

        System.out.println();
        System.out.println("======================================");
        System.out.println("Retrieval Benchmark Initialization");
        System.out.println("======================================");
        System.out.println("Source documents : " + documents.size());
        System.out.println("Total chunks     : " + allChunks.size());
        System.out.println("Candidate K      : " + CANDIDATE_K);
        System.out.println("Evaluation K     : " + TOP_K);
    }

    @Test
    void shouldCompareDenseBm25AndHybridRetrieval()
            throws Exception {

        BenchmarkDataset dataset = loadBenchmarkDataset();

        List<BenchmarkQuery> benchmarkQueries = dataset.queries();

        validateBenchmark(benchmarkQueries);

        EvaluationMetrics denseMetrics = new EvaluationMetrics();
        EvaluationMetrics bm25Metrics = new EvaluationMetrics();
        EvaluationMetrics hybridMetrics = new EvaluationMetrics();

        // Stores the individual retrieval rankings for every query.
        List<QueryRankingExport> rankingExports = new ArrayList<>();

        Map<String, EvaluationMetrics> denseByCategory =
                new LinkedHashMap<>();

        Map<String, EvaluationMetrics> bm25ByCategory =
                new LinkedHashMap<>();

        Map<String, EvaluationMetrics> hybridByCategory =
                new LinkedHashMap<>();

        for (BenchmarkQuery benchmarkQuery : benchmarkQueries) {

            String query = benchmarkQuery.query();

            Map<String, Integer> relevance =
                    benchmarkQuery.relevantDocuments();

            /*
             * Execute Dense + BM25 + Hybrid once.
             *
             * The component rankings and fused ranking are captured
             * from this same execution, ensuring that the diagnostics
             * explain the exact results used by the benchmark.
             */
            HybridRetriever.HybridRetrievalDiagnostics diagnostics =
                    hybridRetriever.retrieveWithDiagnostics(
                            query,
                            CANDIDATE_K
                    );

            // Source-document rankings from the same Hybrid execution.
            List<String> denseResults =
                    getTopDocumentIdsFromRanking(
                            diagnostics.denseRanking()
                    );

            List<String> bm25Results =
                    getTopDocumentIdsFromRanking(
                            diagnostics.bm25Ranking()
                    );

            List<String> hybridResults =
                    getTopHybridDocumentIds(
                            diagnostics.fusedRanking()
                    );

            // Export every fused candidate with its individual contributions.
            List<DocumentFusionExport> fusionExports =
                    diagnostics.fusedRanking()
                            .stream()
                            .map(document ->
                                    new DocumentFusionExport(
                                            document.documentId(),
                                            document.denseRank(),
                                            document.bm25Rank(),
                                            document.denseContribution(),
                                            document.bm25Contribution(),
                                            document.finalRrfScore(),
                                            document.hybridRank(),
                                            relevance.getOrDefault(
                                                    document.documentId(),
                                                    0
                                            )
                                    )
                            )
                            .toList();

            // Store per-query rankings and complete fusion diagnostics.
            rankingExports.add(new QueryRankingExport(
                    benchmarkQuery.id(),
                    benchmarkQuery.category(),
                    query,
                    new LinkedHashMap<>(relevance),
                    denseResults,
                    bm25Results,
                    hybridResults,
                    findFirstRelevantRank(denseResults, relevance),
                    findFirstRelevantRank(bm25Results, relevance),
                    findFirstRelevantRank(hybridResults, relevance),
                    fusionExports
            ));

            // Update overall metrics
            denseMetrics.add(denseResults, relevance);
            bm25Metrics.add(bm25Results, relevance);
            hybridMetrics.add(hybridResults, relevance);

            // Update category-wise metrics
            denseByCategory
                    .computeIfAbsent(
                            benchmarkQuery.category(),
                            key -> new EvaluationMetrics()
                    )
                    .add(denseResults, relevance);

            bm25ByCategory
                    .computeIfAbsent(
                            benchmarkQuery.category(),
                            key -> new EvaluationMetrics()
                    )
                    .add(bm25Results, relevance);

            hybridByCategory
                    .computeIfAbsent(
                            benchmarkQuery.category(),
                            key -> new EvaluationMetrics()
                    )
                    .add(hybridResults, relevance);

            // Print normal query comparison
            printQueryComparison(
                    benchmarkQuery,
                    denseResults,
                    bm25Results,
                    hybridResults
            );

            // Detailed RRF diagnostics for selected queries.
            if (Set.of("Q002", "Q006", "Q047", "Q059", "Q064")
                    .contains(benchmarkQuery.id())) {

                printDetailedFusionRanking(
                        benchmarkQuery.id(),
                        query,
                        relevance,
                        diagnostics.fusedRanking()
                );
            }
        }

        // Export all per-query rankings after the benchmark loop.
        writeRankingExport(rankingExports);

        // Overall comparison
        printComparison(
                "OVERALL RETRIEVAL STRATEGY COMPARISON",
                denseMetrics,
                bm25Metrics,
                hybridMetrics
        );

        // Category-wise comparison
        printCategoryComparison(
                denseByCategory,
                bm25ByCategory,
                hybridByCategory
        );
    }

    /*
     * Exports the retrieval rankings of all benchmark queries
     * into a JSON file inside the Maven target directory.
     */
    private void writeRankingExport(
            List<QueryRankingExport> rankingExports
    ) throws Exception {

        Path outputDirectory = Path.of("target");

        Files.createDirectories(outputDirectory);

        Path outputFile = outputDirectory.resolve(
                "retrieval-ranking-diagnostics.json"
        );

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(outputFile.toFile(), rankingExports);

        System.out.println();
        System.out.println("==========================================");
        System.out.println("PER-QUERY RANKING EXPORT");
        System.out.println("==========================================");
        System.out.println("Total exported queries: " + rankingExports.size());
        System.out.println("Output file: " + outputFile.toAbsolutePath());
    }

    /*
     * Convert the source-document ranking captured by HybridRetriever
     * into the top-five ranking used by benchmark metrics.
     */
    private List<String> getTopDocumentIdsFromRanking(
            List<HybridRetriever.SourceDocumentRanking> rankings
    ) {

        if (rankings == null) {
            return List.of();
        }

        return rankings.stream()
                .limit(TOP_K)
                .map(HybridRetriever.SourceDocumentRanking::documentId)
                .toList();
    }

    /*
     * The fused diagnostics are already sorted by final RRF score.
     * Take the first TOP_K source documents for evaluation.
     */
    private List<String> getTopHybridDocumentIds(
            List<HybridRetriever.HybridDocumentDiagnostic> rankings
    ) {

        if (rankings == null) {
            return List.of();
        }

        return rankings.stream()
                .limit(TOP_K)
                .map(HybridRetriever.HybridDocumentDiagnostic::documentId)
                .toList();
    }

    private void printQueryComparison(
            BenchmarkQuery benchmarkQuery,
            List<String> denseResults,
            List<String> bm25Results,
            List<String> hybridResults
    ) {

        Map<String, Integer> relevance =
                benchmarkQuery.relevantDocuments();

        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println(
                benchmarkQuery.id() + " | " +
                        benchmarkQuery.category()
        );

        System.out.println("Query: " + benchmarkQuery.query());

        System.out.println(
                "Relevant documents: " + relevance
        );

        System.out.println(
                "Dense Rank : " +
                        formatRank(findFirstRelevantRank(
                                denseResults, relevance
                        ))
        );

        System.out.println(
                "BM25 Rank  : " +
                        formatRank(findFirstRelevantRank(
                                bm25Results, relevance
                        ))
        );

        System.out.println(
                "Hybrid Rank: " +
                        formatRank(findFirstRelevantRank(
                                hybridResults, relevance
                        ))
        );

        System.out.println("--------------------------------------------------");
    }

    /*
     * Print the exact RRF fusion diagnostics captured during the
     * benchmark execution. This method does not execute retrieval again.
     */
    private void printDetailedFusionRanking(
            String queryId,
            String query,
            Map<String, Integer> relevance,
            List<HybridRetriever.HybridDocumentDiagnostic> diagnostics
    ) {

        System.out.println(
                "\n========== RRF FUSION DIAGNOSTIC: " + queryId + " =========="
        );
        System.out.println("Query: " + query);
        System.out.println("Relevant documents: " + relevance);

        System.out.printf(
                "%-42s %-8s %-8s %-15s %-15s %-15s %-8s %-5s%n",
                "Document",
                "Dense",
                "BM25",
                "Dense RRF",
                "BM25 RRF",
                "Final RRF",
                "Hybrid",
                "Grade"
        );

        System.out.println(
                "---------------------------------------------------------------------------------------------------------------"
        );

        for (HybridRetriever.HybridDocumentDiagnostic document : diagnostics) {

            System.out.printf(
                    "%-42s %-8s %-8s %-15.8f %-15.8f %-15.8f %-8d %-5d%n",
                    document.documentId(),
                    formatRank(document.denseRank() == null ? -1 : document.denseRank()),
                    formatRank(document.bm25Rank() == null ? -1 : document.bm25Rank()),
                    document.denseContribution(),
                    document.bm25Contribution(),
                    document.finalRrfScore(),
                    document.hybridRank(),
                    relevance.getOrDefault(document.documentId(), 0)
            );
        }

        System.out.println(
                "==============================================================================================================="
        );
    }

    private int findFirstRelevantRank(
            List<String> rankedDocuments,
            Map<String, Integer> relevance
    ) {

        for (int i = 0; i < rankedDocuments.size(); i++) {

            String documentId = rankedDocuments.get(i);

            if (relevance.getOrDefault(documentId, 0) >= 1) {
                return i + 1;
            }
        }

        return -1;
    }

    private String formatRank(int rank) {

        return rank > 0
                ? String.valueOf(rank)
                : "Not Found";
    }

    private void printComparison(
            String title,
            EvaluationMetrics dense,
            EvaluationMetrics bm25,
            EvaluationMetrics hybrid
    ) {

        System.out.println();
        System.out.println();
        System.out.println("==============================================================");
        System.out.println(title);
        System.out.println("==============================================================");

        System.out.printf(
                "%-18s %-12s %-12s %-12s%n",
                "Metric",
                "Dense",
                "BM25",
                "Hybrid RRF"
        );

        System.out.println(
                "--------------------------------------------------------------"
        );

        printRateRow("Hit@1",
                dense.getHitAt1(),
                bm25.getHitAt1(),
                hybrid.getHitAt1());

        printRateRow("Hit@3",
                dense.getHitAt3(),
                bm25.getHitAt3(),
                hybrid.getHitAt3());

        printRateRow("Hit@5",
                dense.getHitAt5(),
                bm25.getHitAt5(),
                hybrid.getHitAt5());

        printDecimalRow("MRR",
                dense.getMRR(),
                bm25.getMRR(),
                hybrid.getMRR());

        printRateRow("Precision@3",
                dense.getPrecisionAt3(),
                bm25.getPrecisionAt3(),
                hybrid.getPrecisionAt3());

        printRateRow("Precision@5",
                dense.getPrecisionAt5(),
                bm25.getPrecisionAt5(),
                hybrid.getPrecisionAt5());

        printRateRow("Recall@3",
                dense.getRecallAt3(),
                bm25.getRecallAt3(),
                hybrid.getRecallAt3());

        printRateRow("Recall@5",
                dense.getRecallAt5(),
                bm25.getRecallAt5(),
                hybrid.getRecallAt5());

        printDecimalRow("NDCG@3",
                dense.getNdcgAt3(),
                bm25.getNdcgAt3(),
                hybrid.getNdcgAt3());

        printDecimalRow("NDCG@5",
                dense.getNdcgAt5(),
                bm25.getNdcgAt5(),
                hybrid.getNdcgAt5());

        System.out.println(
                "--------------------------------------------------------------"
        );

        System.out.println(
                "Total queries: " + dense.totalQueries
        );

        System.out.println(
                "=============================================================="
        );
    }

    private void printRateRow(
            String name,
            double dense,
            double bm25,
            double hybrid
    ) {

        System.out.printf(
                "%-18s %-12s %-12s %-12s%n",
                name,
                formatPercentage(dense),
                formatPercentage(bm25),
                formatPercentage(hybrid)
        );
    }

    private void printDecimalRow(
            String name,
            double dense,
            double bm25,
            double hybrid
    ) {

        System.out.printf(
                "%-18s %-12.4f %-12.4f %-12.4f%n",
                name,
                dense,
                bm25,
                hybrid
        );
    }

    private String formatPercentage(double value) {

        return String.format("%.1f%%", value * 100.0);
    }

    private void printCategoryComparison(
            Map<String, EvaluationMetrics> denseByCategory,
            Map<String, EvaluationMetrics> bm25ByCategory,
            Map<String, EvaluationMetrics> hybridByCategory
    ) {

        System.out.println();
        System.out.println();
        System.out.println("############################################");
        System.out.println("CATEGORY-WISE RETRIEVAL EVALUATION");
        System.out.println("############################################");

        for (String category : denseByCategory.keySet()) {

            System.out.println();
            System.out.println("CATEGORY: " + category);

            printComparison(
                    category,
                    denseByCategory.get(category),
                    bm25ByCategory.get(category),
                    hybridByCategory.get(category)
            );
        }
    }

    private BenchmarkDataset loadBenchmarkDataset()
            throws Exception {

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(BENCHMARK_PATH);

        if (inputStream == null) {

            throw new IllegalStateException(
                    "Benchmark file not found: " + BENCHMARK_PATH
            );
        }

        try (inputStream) {

            return objectMapper.readValue(
                    inputStream,
                    BenchmarkDataset.class
            );
        }
    }

    private void validateBenchmark(
            List<BenchmarkQuery> queries
    ) {

        assertNotNull(queries, "Benchmark queries cannot be null");
        assertFalse(queries.isEmpty(), "Benchmark must contain queries");

        for (BenchmarkQuery query : queries) {

            if (query.id() == null || query.id().isBlank()) {
                throw new IllegalArgumentException(
                        "Benchmark query ID cannot be blank"
                );
            }

            if (query.category() == null || query.category().isBlank()) {
                throw new IllegalArgumentException(
                        "Category cannot be blank: " + query.id()
                );
            }

            if (query.query() == null || query.query().isBlank()) {
                throw new IllegalArgumentException(
                        "Query cannot be blank: " + query.id()
                );
            }

            Map<String, Integer> relevant =
                    query.relevantDocuments();

            if (relevant == null || relevant.isEmpty()) {
                throw new IllegalArgumentException(
                        "Relevant documents missing: " + query.id()
                );
            }

            for (Map.Entry<String, Integer> entry :
                    relevant.entrySet()) {

                String documentId = entry.getKey();
                Integer grade = entry.getValue();

                if (documentId == null || documentId.isBlank()) {
                    throw new IllegalArgumentException(
                            "Invalid document ID: " + query.id()
                    );
                }

                if (grade == null || grade < 1 || grade > 3) {
                    throw new IllegalArgumentException(
                            "Invalid relevance grade in " + query.id()
                    );
                }
            }
        }

        System.out.println();
        System.out.println(
                "Validated benchmark queries: " + queries.size()
        );
    }

    // JSON mapping records

    private record BenchmarkDataset(
            String benchmarkVersion,
            String description,
            Map<String, Object> sourceKnowledgeBase,
            Map<String, String> relevanceScale,
            List<String> labelingNotes,
            List<BenchmarkQuery> queries
    ) {
    }

    private record BenchmarkQuery(
            String id,
            String category,
            String query,
            Map<String, Integer> relevantDocuments
    ) {
    }

    /*
     * Stores the retrieval results for one benchmark query.
     *
     * The rankings contain normalized source-document IDs,
     * not individual chunk IDs.
     *
     * A relevant rank of -1 means no relevant document
     * was retrieved in the top 5.
     */
    private record QueryRankingExport(
            String queryId,
            String category,
            String query,
            Map<String, Integer> relevantDocuments,
            List<String> denseTop5,
            List<String> bm25Top5,
            List<String> hybridTop5,
            int denseRelevantRank,
            int bm25RelevantRank,
            int hybridRelevantRank,
            List<DocumentFusionExport> fusionDiagnostics
    ) {
    }

    /*
     * Per-source-document fusion information.
     *
     * A null rank means that the document was not returned by
     * that component retriever. Its corresponding contribution
     * is zero.
     */
    private record DocumentFusionExport(
            String documentId,
            Integer denseRank,
            Integer bm25Rank,
            double denseContribution,
            double bm25Contribution,
            double finalRrfScore,
            int hybridRank,
            int relevanceGrade
    ) {
    }

    /*
     * Accumulates metrics across benchmark queries.
     *
     * Precision uses fixed K as denominator.
     * Recall uses the number of relevant source documents.
     * NDCG uses graded relevance: gain = 2^grade - 1.
     */
    private static class EvaluationMetrics {

        private int totalQueries;

        private int hitAt1;
        private int hitAt3;
        private int hitAt5;

        private double reciprocalRankSum;

        private double precisionAt3Sum;
        private double precisionAt5Sum;

        private double recallAt3Sum;
        private double recallAt5Sum;

        private double ndcgAt3Sum;
        private double ndcgAt5Sum;

        void add(
                List<String> rankedDocuments,
                Map<String, Integer> relevance
        ) {

            totalQueries++;

            int relevantAt1 = countRelevant(
                    rankedDocuments, relevance, 1
            );

            int relevantAt3 = countRelevant(
                    rankedDocuments, relevance, 3
            );

            int relevantAt5 = countRelevant(
                    rankedDocuments, relevance, 5
            );

            // Hit@K
            if (relevantAt1 > 0) {
                hitAt1++;
            }

            if (relevantAt3 > 0) {
                hitAt3++;
            }

            if (relevantAt5 > 0) {
                hitAt5++;
            }

            // MRR
            int firstRelevantRank =
                    findFirstRelevantRank(rankedDocuments, relevance);

            if (firstRelevantRank > 0) {
                reciprocalRankSum += 1.0 / firstRelevantRank;
            }

            // Precision@K
            precisionAt3Sum += relevantAt3 / 3.0;
            precisionAt5Sum += relevantAt5 / 5.0;

            // Recall@K
            int totalRelevantDocuments = relevance.size();

            recallAt3Sum +=
                    relevantAt3 / (double) totalRelevantDocuments;

            recallAt5Sum +=
                    relevantAt5 / (double) totalRelevantDocuments;

            // NDCG@K
            ndcgAt3Sum += calculateNdcg(
                    rankedDocuments, relevance, 3
            );

            ndcgAt5Sum += calculateNdcg(
                    rankedDocuments, relevance, 5
            );
        }

        private int countRelevant(
                List<String> rankedDocuments,
                Map<String, Integer> relevance,
                int k
        ) {

            int count = 0;

            int limit = Math.min(k, rankedDocuments.size());

            for (int i = 0; i < limit; i++) {

                String documentId = rankedDocuments.get(i);

                if (relevance.getOrDefault(documentId, 0) >= 1) {
                    count++;
                }
            }

            return count;
        }

        private int findFirstRelevantRank(
                List<String> rankedDocuments,
                Map<String, Integer> relevance
        ) {

            for (int i = 0; i < rankedDocuments.size(); i++) {

                String documentId = rankedDocuments.get(i);

                if (relevance.getOrDefault(documentId, 0) >= 1) {
                    return i + 1;
                }
            }

            return -1;
        }

        private double calculateNdcg(
                List<String> rankedDocuments,
                Map<String, Integer> relevance,
                int k
        ) {

            double dcg = 0.0;

            int limit = Math.min(k, rankedDocuments.size());

            for (int i = 0; i < limit; i++) {

                String documentId = rankedDocuments.get(i);

                int grade = relevance.getOrDefault(documentId, 0);

                double gain = Math.pow(2, grade) - 1;

                double discount = log2(i + 2);

                dcg += gain / discount;
            }

            List<Integer> idealGrades =
                    new ArrayList<>(relevance.values());

            idealGrades.sort(Comparator.reverseOrder());

            double idealDcg = 0.0;

            int idealLimit = Math.min(k, idealGrades.size());

            for (int i = 0; i < idealLimit; i++) {

                int grade = idealGrades.get(i);

                double gain = Math.pow(2, grade) - 1;

                double discount = log2(i + 2);

                idealDcg += gain / discount;
            }

            if (idealDcg == 0.0) {
                return 0.0;
            }

            return dcg / idealDcg;
        }

        private double log2(double value) {

            return Math.log(value) / Math.log(2);
        }

        // Aggregate metric getters

        double getHitAt1() {
            return hitAt1 / (double) totalQueries;
        }

        double getHitAt3() {
            return hitAt3 / (double) totalQueries;
        }

        double getHitAt5() {
            return hitAt5 / (double) totalQueries;
        }

        double getMRR() {
            return reciprocalRankSum / totalQueries;
        }

        double getPrecisionAt3() {
            return precisionAt3Sum / totalQueries;
        }

        double getPrecisionAt5() {
            return precisionAt5Sum / totalQueries;
        }

        double getRecallAt3() {
            return recallAt3Sum / totalQueries;
        }

        double getRecallAt5() {
            return recallAt5Sum / totalQueries;
        }

        double getNdcgAt3() {
            return ndcgAt3Sum / totalQueries;
        }

        double getNdcgAt5() {
            return ndcgAt5Sum / totalQueries;
        }
    }
}