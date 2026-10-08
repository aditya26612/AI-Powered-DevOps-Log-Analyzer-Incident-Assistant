package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RetrievalResultTest {

    @Test
    void shouldBuildRetrievalResultSuccessfully() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("Database troubleshooting")
                        .metadata(Map.of("source", "runbook"))
                        .build();

        RetrievalResult result =
                RetrievalResult.builder()
                        .document(document)
                        .score(0.92)
                        .build();

        assertSame(document, result.getDocument());
        assertEquals(0.92, result.getScore());
    }
}