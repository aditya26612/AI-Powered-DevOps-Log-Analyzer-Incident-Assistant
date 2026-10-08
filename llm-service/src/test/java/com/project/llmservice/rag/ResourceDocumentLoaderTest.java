package com.project.llmservice.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDocumentLoaderTest {

    private final ResourceDocumentLoader loader =
            new ResourceDocumentLoader();

    @Test
    void shouldLoadAllKnowledgeDocuments() {

        List<KnowledgeDocument> documents = loader.load();

        assertEquals(21, documents.size());

        assertTrue(
                documents.stream()
                        .allMatch(document ->
                                document.getContent() != null &&
                                        !document.getContent().isBlank())
        );

        assertTrue(
                documents.stream()
                        .anyMatch(document ->
                                document.getId().startsWith("docker-"))
        );

        assertTrue(
                documents.stream()
                        .anyMatch(document ->
                                document.getId().startsWith("kubernetes-"))
        );

        assertTrue(
                documents.stream()
                        .anyMatch(document ->
                                document.getId().startsWith("spring-boot-"))
        );

        assertTrue(
                documents.stream()
                        .anyMatch(document ->
                                document.getId().startsWith("nginx-"))
        );

        assertTrue(
                documents.stream()
                        .anyMatch(document ->
                                document.getId().startsWith("postgresql-"))
        );
    }
}