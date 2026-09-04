package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDocumentLoaderTest {

    @Test
    void shouldLoadKnowledgeDocument() {

        ResourceDocumentLoader loader =
                new ResourceDocumentLoader();

        List<KnowledgeDocument> documents =
                loader.load();

        assertNotNull(documents);
        assertFalse(documents.isEmpty());

        KnowledgeDocument document = documents.get(0);

        assertNotNull(document.getId());
        assertNotNull(document.getContent());
        assertFalse(document.getContent().isBlank());

        assertEquals(
                document.getId(),
                document.getMetadata().get("source")
        );
    }
}