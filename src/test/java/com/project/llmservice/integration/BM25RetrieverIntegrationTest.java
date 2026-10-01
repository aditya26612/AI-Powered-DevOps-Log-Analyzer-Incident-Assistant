package com.project.llmservice.integration;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.ResourceDocumentLoader;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.bm25.BM25Retriever;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BM25RetrieverIntegrationTest {

    @Autowired
    private ResourceDocumentLoader documentLoader;

    @Autowired
    private DocumentSplitter documentSplitter;

    @Autowired
    private BM25Retriever bm25Retriever;

    @BeforeEach
    void setUp() {

        List<KnowledgeDocument> documents =
                documentLoader.load();

        bm25Retriever.indexDocuments(documents);
    }

    @Test
    void shouldRetrieveRelevantDocumentForDatabaseQuery() {

        List<RetrievalResult> results =
                bm25Retriever.retrieve(
                        "database connection failed",
                        5
                );

        assertFalse(results.isEmpty());

        assertEquals(
                "spring-boot-datasource-error.md",
                results.get(0)
                        .getDocument()
                        .getId()
                        .replaceAll("-chunk-\\d+$", "")
        );
    }

    @Test
    void shouldRetrieveRelevantDocumentForDockerQuery() {

        List<RetrievalResult> results =
                bm25Retriever.retrieve(
                        "Docker container keeps restarting",
                        5
                );

        assertFalse(results.isEmpty());

        assertTrue(
                results.stream()
                        .anyMatch(result ->
                                result.getDocument()
                                        .getId()
                                        .startsWith(
                                                "docker-container-startup-failure"
                                        )
                        )
        );
    }

    @Test
    void shouldRetrieveRelevantDocumentForKubernetesQuery() {

        List<RetrievalResult> results =
                bm25Retriever.retrieve(
                        "Kubernetes pod is stuck pending",
                        5
                );

        assertFalse(results.isEmpty());

        assertTrue(
                results.stream()
                        .anyMatch(result ->
                                result.getDocument()
                                        .getId()
                                        .startsWith(
                                                "kubernetes-pod-pending"
                                        )
                        )
        );
    }
}