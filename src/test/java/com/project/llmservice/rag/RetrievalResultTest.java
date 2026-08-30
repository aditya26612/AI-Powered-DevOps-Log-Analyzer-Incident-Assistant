package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetrievalResultTest {

    @Test
    void shouldCreateRetrievalResult() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("PostgreSQL connection troubleshooting")
                        .metadata(
                                Map.of("source", "database-guide")
                        )
                        .build();

        RetrievalResult result =
                RetrievalResult.builder()
                        .document(document)
                        .score(0.92)
                        .build();

        assertNotNull(result);

        assertEquals(
                document,
                result.getDocument()
        );

        assertEquals(
                0.92,
                result.getScore()
        );
    }
}