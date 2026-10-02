package com.project.llmservice.integration;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.RagIngestionService;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.Retriever;
import com.project.llmservice.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;




import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VectorRetrievalIntegrationTest {

    @Autowired
    private RagIngestionService ragIngestionService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    @Qualifier("vectorRetriever")
    private Retriever retriever;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ragIngestionService.ingest();
    }

    private record BenchmarkQuery(
            String query,
            String expectedDocument
    ) {
    }



    private List<BenchmarkQuery> loadBenchmark()
            throws Exception {

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "retrieval/retrieval-benchmark.json"
                        );

        assertNotNull(
                inputStream,
                "Retrieval benchmark file was not found"
        );

        return objectMapper.readValue(
                inputStream,
                new TypeReference<List<BenchmarkQuery>>() {
                }
        );
    }

    private int findRelevantRank(
            List<RetrievalResult> results,
            String expectedDocument
    ) {

        for (int i = 0; i < results.size(); i++) {

            String documentId =
                    results.get(i)
                            .getDocument()
                            .getId();

            if (documentId.startsWith(expectedDocument)) {
                return i + 1;
            }
        }

        return -1;
    }

    private double percentage(
            int value,
            int total
    ) {

        return Math.round(
                ((double) value / total) * 10000
        ) / 100.0;
    }

    @Test
    void shouldEvaluateDenseRetrievalBaseline() throws Exception {

        List<BenchmarkQuery> benchmark =
                loadBenchmark();

        int hitAt1 = 0;
        int hitAt3 = 0;
        int hitAt5 = 0;

        double reciprocalRankSum = 0.0;

        for (BenchmarkQuery item : benchmark) {

            float[] queryEmbedding =
                    embeddingService.embed(
                            item.query()
                    );

            List<RetrievalResult> results =
                    vectorStore.search(
                            queryEmbedding,
                            5
                    );

            int rank =
                    findRelevantRank(
                            results,
                            item.expectedDocument()
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

            System.out.println(
                    item.query()
                            + " | rank="
                            + rank
            );
        }

        double mrr =
                reciprocalRankSum / benchmark.size();

        System.out.println();
        System.out.println("==============================================");
        System.out.println("DENSE RETRIEVAL BASELINE");
        System.out.println("==============================================");
        System.out.println("Total queries : " + benchmark.size());
        System.out.println(
                "Hit@1        : "
                        + hitAt1
                        + "/"
                        + benchmark.size()
                        + " ("
                        + percentage(hitAt1, benchmark.size())
                        + "%)"
        );
        System.out.println(
                "Hit@3        : "
                        + hitAt3
                        + "/"
                        + benchmark.size()
                        + " ("
                        + percentage(hitAt3, benchmark.size())
                        + "%)"
        );
        System.out.println(
                "Hit@5        : "
                        + hitAt5
                        + "/"
                        + benchmark.size()
                        + " ("
                        + percentage(hitAt5, benchmark.size())
                        + "%)"
        );
        System.out.println(
                "MRR          : "
                        + String.format("%.4f", mrr)
        );
        System.out.println("==============================================");
    }

    @Test
    void shouldRetrieveRelevantDocumentsForDevOpsQueries() {

        evaluateQuery(
                "database connection failed",
                "spring-boot-datasource-error"
        );

        evaluateQuery(
                "Docker container keeps restarting",
                "docker-container-startup-failure"
        );

        evaluateQuery(
                "Kubernetes pod is stuck pending",
                "kubernetes-pod-pending"
        );

        evaluateQuery(
                "Nginx upstream request is timing out",
                "nginx-upstream-timeout"
        );

        evaluateQuery(
                "PostgreSQL has too many database connections",
                "postgresql-too-many-connections"
        );

        evaluateQuery(
                "Spring Boot application cannot bind to the port",
                "spring-boot-port-binding-error"
        );
    }

    private void evaluateQuery(
            String query,
            String expectedDocumentPrefix
    ) {

        float[] queryEmbedding =
                embeddingService.embed(query);

        // Raw vector search
        List<RetrievalResult> rawResults =
                vectorStore.search(
                        queryEmbedding,
                        5
                );

        // Retrieval after similarity threshold
        List<RetrievalResult> filteredResults =
                retriever.retrieve(
                        query,
                        5
                );

        System.out.println();
        System.out.println("==============================================");
        System.out.println("QUERY: " + query);
        System.out.println("EXPECTED: " + expectedDocumentPrefix);

        System.out.println();
        System.out.println("RAW TOP 5 RESULTS:");

        for (int i = 0; i < rawResults.size(); i++) {

            RetrievalResult result =
                    rawResults.get(i);

            System.out.println(
                    (i + 1)
                            + ". "
                            + result.getDocument().getId()
                            + " | Score: "
                            + result.getScore()
            );
        }

        System.out.println();
        System.out.println("AFTER THRESHOLD (0.70):");

        for (int i = 0; i < filteredResults.size(); i++) {

            RetrievalResult result =
                    filteredResults.get(i);

            System.out.println(
                    (i + 1)
                            + ". "
                            + result.getDocument().getId()
                            + " | Score: "
                            + result.getScore()
            );
        }

        boolean foundInRaw =
                rawResults.stream()
                        .anyMatch(result ->
                                result.getDocument()
                                        .getId()
                                        .startsWith(
                                                expectedDocumentPrefix
                                        )
                        );

        boolean foundAfterThreshold =
                filteredResults.stream()
                        .anyMatch(result ->
                                result.getDocument()
                                        .getId()
                                        .startsWith(
                                                expectedDocumentPrefix
                                        )
                        );

        System.out.println();
        System.out.println(
                "Expected document found in raw top 5: "
                        + foundInRaw
        );

        System.out.println(
                "Expected document found after threshold: "
                        + foundAfterThreshold
        );

        System.out.println("==============================================");

        assertFalse(
                rawResults.isEmpty(),
                "Raw retrieval returned no results for query: "
                        + query
        );

        assertTrue(
                foundInRaw,
                "Expected document was not found in raw top 5: "
                        + expectedDocumentPrefix
        );
    }
}