package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRetrieverTest {

    @Test
    void shouldReturnRelevantDocuments() {

        KnowledgeDocument databaseDocument =
                KnowledgeDocument.builder()
                        .id("database")
                        .content("Database connection failed because PostgreSQL is unreachable.")
                        .metadata(null)
                        .build();

        KnowledgeDocument kubernetesDocument =
                KnowledgeDocument.builder()
                        .id("kubernetes")
                        .content("Kubernetes pods can restart when containers fail.")
                        .metadata(null)
                        .build();

        DocumentLoader documentLoader = () ->
                List.of(databaseDocument, kubernetesDocument);

        SimpleRetriever retriever =
                new SimpleRetriever(documentLoader);

        List<RetrievalResult> results =
                retriever.retrieve("database connection failed", 1);

        assertNotNull(results);
        assertEquals(1, results.size());

        RetrievalResult result = results.get(0);

        assertEquals("database", result.getDocument().getId());
        assertTrue(result.getScore() > 0);
    }

    @Test
    void shouldReturnResultsOrderedByScore() {

        KnowledgeDocument highMatch =
                KnowledgeDocument.builder()
                        .id("high")
                        .content("database connection failed database")
                        .metadata(null)
                        .build();

        KnowledgeDocument lowMatch =
                KnowledgeDocument.builder()
                        .id("low")
                        .content("database issue")
                        .metadata(null)
                        .build();

        DocumentLoader documentLoader = () ->
                List.of(lowMatch, highMatch);

        SimpleRetriever retriever =
                new SimpleRetriever(documentLoader);

        List<RetrievalResult> results =
                retriever.retrieve("database connection failed", 2);

        assertEquals(2, results.size());

        assertEquals(
                "high",
                results.get(0).getDocument().getId()
        );

        assertTrue(
                results.get(0).getScore()
                        >= results.get(1).getScore()
        );
    }

    @Test
    void shouldRespectTopK() {

        KnowledgeDocument document1 =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("database connection failed")
                        .metadata(null)
                        .build();

        KnowledgeDocument document2 =
                KnowledgeDocument.builder()
                        .id("doc-2")
                        .content("database connection issue")
                        .metadata(null)
                        .build();

        DocumentLoader documentLoader = () ->
                List.of(document1, document2);

        SimpleRetriever retriever =
                new SimpleRetriever(documentLoader);

        List<RetrievalResult> results =
                retriever.retrieve("database connection", 1);

        assertEquals(1, results.size());
    }

    @Test
    void shouldReturnEmptyListForInvalidQuery() {

        DocumentLoader documentLoader = () ->
                List.of();

        SimpleRetriever retriever =
                new SimpleRetriever(documentLoader);

        assertTrue(
                retriever.retrieve("", 5).isEmpty()
        );

        assertTrue(
                retriever.retrieve(null, 5).isEmpty()
        );

        assertTrue(
                retriever.retrieve("database", 0).isEmpty()
        );
    }

    @Test
    void shouldIgnoreDocumentsWithNoMatchingTerms() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("kubernetes")
                        .content("Kubernetes pods can restart.")
                        .metadata(null)
                        .build();

        DocumentLoader documentLoader = () ->
                List.of(document);

        SimpleRetriever retriever =
                new SimpleRetriever(documentLoader);

        List<RetrievalResult> results =
                retriever.retrieve("database failure", 5);

        assertTrue(results.isEmpty());
    }
}