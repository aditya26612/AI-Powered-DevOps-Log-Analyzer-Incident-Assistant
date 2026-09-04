package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentLoaderTest {

    @Test
    void shouldLoadKnowledgeDocuments() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-001")
                        .content("Test document")
                        .metadata(Map.of("source", "test"))
                        .build();

        DocumentLoader loader = () -> List.of(document);

        List<KnowledgeDocument> documents = loader.load();

        assertEquals(1, documents.size());
        assertEquals("doc-001", documents.get(0).getId());
        assertEquals(
                "Test document",
                documents.get(0).getContent()
        );
    }
}