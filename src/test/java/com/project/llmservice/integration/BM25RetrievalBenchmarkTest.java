package com.project.llmservice.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.rag.ResourceDocumentLoader;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.bm25.BM25Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BM25RetrievalBenchmarkTest {

    @Autowired
    private ResourceDocumentLoader documentLoader;

    @Autowired
    private BM25Retriever bm25Retriever;

    @Autowired
    private ObjectMapper objectMapper;

    private List<BenchmarkQuery> benchmarkQueries;

    @BeforeEach
    void setUp() throws Exception {

        bm25Retriever.indexDocuments(
                documentLoader.load()
        );

        try (InputStream inputStream =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(
                                     "retrieval/retrieval-benchmark.json"
                             )) {

            benchmarkQueries =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<>() {}
                    );
        }
    }

    @Test
    void shouldEvaluateBM25Baseline() {

        int hitAt1 = 0;
        int hitAt3 = 0;
        int hitAt5 = 0;

        double reciprocalRankSum = 0.0;

        for (BenchmarkQuery benchmarkQuery : benchmarkQueries) {

            List<RetrievalResult> results =
                    bm25Retriever.retrieve(
                            benchmarkQuery.query(),
                            5
                    );

            String expected =
                    benchmarkQuery.expectedDocument();

            int rank = -1;

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

                if (actual.equals(expected)) {
                    rank = i + 1;
                    break;
                }
            }

            if (rank == 1) {
                hitAt1++;
            }

            if (rank >= 1 && rank <= 3) {
                hitAt3++;
            }

            if (rank >= 1 && rank <= 5) {
                hitAt5++;
            }

            if (rank >= 1) {
                reciprocalRankSum += 1.0 / rank;
            }

            System.out.printf(
                    "%-50s -> expected=%s%n",
                    benchmarkQuery.query(),
                    benchmarkQuery.expectedDocument()
            );

            for (int i = 0; i < results.size(); i++) {
                System.out.printf(
                        "    #%d -> %s%n",
                        i + 1,
                        results.get(i)
                                .getDocument()
                                .getId()
                );
            }
        }

        int total = benchmarkQueries.size();

        double hitAt1Score =
                (double) hitAt1 / total;

        double hitAt3Score =
                (double) hitAt3 / total;

        double hitAt5Score =
                (double) hitAt5 / total;

        double mrr =
                reciprocalRankSum / total;

        System.out.println();
        System.out.println("===== BM25 BASELINE =====");

        System.out.printf(
                "Total queries : %d%n",
                total
        );

        System.out.printf(
                "Hit@1        : %d/%d (%.1f%%)%n",
                hitAt1,
                total,
                hitAt1Score * 100
        );

        System.out.printf(
                "Hit@3        : %d/%d (%.1f%%)%n",
                hitAt3,
                total,
                hitAt3Score * 100
        );

        System.out.printf(
                "Hit@5        : %d/%d (%.1f%%)%n",
                hitAt5,
                total,
                hitAt5Score * 100
        );

        System.out.printf(
                "MRR          : %.4f%n",
                mrr
        );

        assertEquals(
                total,
                benchmarkQueries.size()
        );
    }

    private record BenchmarkQuery(
            String query,
            String expectedDocument
    ) {
    }
}