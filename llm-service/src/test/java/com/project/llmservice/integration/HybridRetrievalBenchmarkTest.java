package com.project.llmservice.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.rag.HybridRetriever;
import com.project.llmservice.rag.ResourceDocumentLoader;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.bm25.BM25Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HybridRetrievalBenchmarkTest {

    @Autowired
    private ResourceDocumentLoader documentLoader;

    @Autowired
    private BM25Retriever bm25Retriever;

    @Autowired
    private HybridRetriever hybridRetriever;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        bm25Retriever.indexDocuments(
                documentLoader.load()
        );
    }

    @Test
    void shouldEvaluateHybridRetrievalBenchmark()
            throws Exception {

        List<BenchmarkQuery> benchmarkQueries =
                loadBenchmarkQueries();

        int hitAt1 = 0;
        int hitAt3 = 0;
        int hitAt5 = 0;

        double reciprocalRankSum = 0.0;

        for (BenchmarkQuery benchmarkQuery
                : benchmarkQueries) {

            List<RetrievalResult> results =
                    hybridRetriever.retrieve(
                            benchmarkQuery.query(),
                            5
                    );

            int rank = findExpectedDocumentRank(
                    results,
                    benchmarkQuery.expectedDocument()
            );

            if (rank == 1) {
                hitAt1++;
            }

            if (rank > 0 && rank <= 3) {
                hitAt3++;
            }

            if (rank > 0 && rank <= 5) {
                hitAt5++;
            }

            if (rank > 0) {
                reciprocalRankSum += 1.0 / rank;
            }

            System.out.println();
            System.out.println(
                    "Query: "
                            + benchmarkQuery.query()
            );

            System.out.println(
                    "Expected: "
                            + benchmarkQuery.expectedDocument()
            );

            System.out.println(
                    "Rank: "
                            + rank
            );

            System.out.println("Top 5:");

            for (int i = 0;
                 i < results.size();
                 i++) {

                System.out.println(
                        (i + 1)
                                + ". "
                                + results.get(i)
                                .getDocument()
                                .getId()
                                + " | score="
                                + results.get(i)
                                .getScore()
                );
            }
        }

        int totalQueries =
                benchmarkQueries.size();

        double hitAt1Percentage =
                (hitAt1 * 100.0) / totalQueries;

        double hitAt3Percentage =
                (hitAt3 * 100.0) / totalQueries;

        double hitAt5Percentage =
                (hitAt5 * 100.0) / totalQueries;

        double mrr =
                reciprocalRankSum / totalQueries;

        System.out.println();
        System.out.println(
                "======================================"
        );
        System.out.println(
                "Final Hybrid RRF Retrieval Benchmark"
        );
        System.out.println(
                "======================================"
        );

        System.out.printf(
                "Total queries : %d%n",
                totalQueries
        );

        System.out.printf(
                "Hit@1        : %d/%d (%.1f%%)%n",
                hitAt1,
                totalQueries,
                hitAt1Percentage
        );

        System.out.printf(
                "Hit@3        : %d/%d (%.1f%%)%n",
                hitAt3,
                totalQueries,
                hitAt3Percentage
        );

        System.out.printf(
                "Hit@5        : %d/%d (%.1f%%)%n",
                hitAt5,
                totalQueries,
                hitAt5Percentage
        );

        System.out.printf(
                "MRR          : %.4f%n",
                mrr
        );

        System.out.println(
                "======================================"
        );

        assertTrue(
                totalQueries > 0,
                "Benchmark must contain queries"
        );
    }

    private int findExpectedDocumentRank(
            List<RetrievalResult> results,
            String expectedDocument
    ) {

        for (int i = 0; i < results.size(); i++) {

            String actual =
                    results.get(i)
                            .getDocument()
                            .getId()
                            .replaceAll(
                                    "-chunk-\\d+$",
                                    ""
                            )
                            .replaceAll(
                                    "\\.md$",
                                    ""
                            );

            if (actual.equals(expectedDocument)) {
                return i + 1;
            }
        }

        return -1;
    }

    private List<BenchmarkQuery> loadBenchmarkQueries()
            throws Exception {

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "retrieval/retrieval-benchmark.json"
                        );

        if (inputStream == null) {
            throw new IllegalStateException(
                    "Benchmark file not found"
            );
        }

        return objectMapper.readValue(
                inputStream,
                new TypeReference<List<BenchmarkQuery>>() {}
        );
    }

    private record BenchmarkQuery(
            String query,
            String expectedDocument
    ) {
    }
}