package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeDocumentTest {

    @Test
    void shouldCreateKnowledgeDocument() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("PostgreSQL connection troubleshooting")
                        .metadata(
                                Map.of(
                                        "source", "database-guide",
                                        "type", "troubleshooting"
                                )
                        )
                        .build();

        assertNotNull(document);

        assertEquals(
                "doc-001",
                document.getId()
        );

        assertEquals(
                "PostgreSQL connection troubleshooting",
                document.getContent()
        );

        assertEquals(
                "database-guide",
                document.getMetadata().get("source")
        );

        assertEquals(
                "troubleshooting",
                document.getMetadata().get("type")
        );
    }
}