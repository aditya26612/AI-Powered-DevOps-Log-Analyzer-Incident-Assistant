package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeDocumentTest {

    @Test
    void shouldBuildKnowledgeDocumentSuccessfully() {

        Map<String, String> metadata = Map.of(
                "source", "runbook",
                "service", "payment-service"
        );

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("Database connection troubleshooting")
                        .metadata(metadata)
                        .build();

        assertEquals("doc-001", document.getId());
        assertEquals(
                "Database connection troubleshooting",
                document.getContent()
        );
        assertEquals(metadata, document.getMetadata());
    }
}