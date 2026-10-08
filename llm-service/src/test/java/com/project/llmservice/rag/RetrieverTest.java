package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrieverTest {

    @Test
    void shouldRetrieveResults() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("Database troubleshooting")
                        .metadata(Map.of("source", "runbook"))
                        .build();

        RetrievalResult result =
                RetrievalResult.builder()
                        .document(document)
                        .score(0.95)
                        .build();

        Retriever retriever =
                (query, topK) -> List.of(result);

        List<RetrievalResult> results =
                retriever.retrieve(
                        "database connection failed",
                        1
                );

        assertEquals(1, results.size());
        assertEquals(0.95, results.get(0).getScore());
        assertEquals(
                "doc-001",
                results.get(0).getDocument().getId()
        );
    }
}