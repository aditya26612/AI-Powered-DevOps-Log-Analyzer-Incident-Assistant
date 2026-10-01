package com.project.llmservice.integration;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.RagIngestionService;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VectorRetrievalIntegrationTest {

    @Autowired
    private RagIngestionService ragIngestionService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        ragIngestionService.ingest();
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

        List<RetrievalResult> results =
                vectorStore.search(
                        queryEmbedding,
                        5
                );

        assertFalse(
                results.isEmpty(),
                "No results returned for query: " + query
        );

        boolean relevantDocumentFound =
                results.stream()
                        .anyMatch(result ->
                                result.getDocument()
                                        .getId()
                                        .startsWith(
                                                expectedDocumentPrefix
                                        )
                        );

        System.out.println();
        System.out.println("==============================================");
        System.out.println("QUERY: " + query);
        System.out.println("EXPECTED: " + expectedDocumentPrefix);
        System.out.println("TOP RESULTS:");

        for (int i = 0; i < results.size(); i++) {

            RetrievalResult result =
                    results.get(i);

            System.out.println(
                    (i + 1)
                            + ". "
                            + result.getDocument().getId()
            );

            System.out.println(
                    "   Score: "
                            + result.getScore()
            );
        }

        System.out.println(
                "Relevant document found in top 5: "
                        + relevantDocumentFound
        );

        System.out.println("==============================================");

        assertTrue(
                relevantDocumentFound,
                "Expected relevant document '"
                        + expectedDocumentPrefix
                        + "' was not found in top 5 results for query: "
                        + query
        );
    }
}