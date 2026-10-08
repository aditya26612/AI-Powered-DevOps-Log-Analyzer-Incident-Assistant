package com.project.llmservice.integration;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.properties.RagProperties;
import com.project.llmservice.rag.*;
import com.project.llmservice.rag.splitter.SimpleDocumentSplitter;
import com.project.llmservice.vectorstore.InMemoryVectorStore;
import com.project.llmservice.vectorstore.VectorStoreService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagIngestionRetrievalIntegrationTest {

    @Test
    void shouldIngestAndRetrieveDocument() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("database-doc")
                        .content(
                                "PostgreSQL database connection troubleshooting"
                        )
                        .build();

        DocumentLoader documentLoader =
                mock(DocumentLoader.class);

        when(documentLoader.load())
                .thenReturn(List.of(document));

        SimpleDocumentSplitter documentSplitter =
                new SimpleDocumentSplitter();

        EmbeddingService embeddingService =
                mock(EmbeddingService.class);

        InMemoryVectorStore vectorStore =
                new InMemoryVectorStore();

        VectorStoreService vectorStoreService =
                new VectorStoreService(
                        embeddingService,
                        vectorStore
                );

        RagIngestionService ingestionService =
                new RagIngestionService(
                        documentLoader,
                        documentSplitter,
                        vectorStoreService
                );

        when(
                embeddingService.embed(
                        "PostgreSQL database connection troubleshooting"
                )
        ).thenReturn(
                new float[]{
                        1.0f,
                        0.0f,
                        0.0f
                }
        );

        ingestionService.ingest();

        when(
                embeddingService.embed(
                        "database connection"
                )
        ).thenReturn(
                new float[]{
                        1.0f,
                        0.0f,
                        0.0f
                }
        );

        RagProperties ragProperties =
                new RagProperties();

        ragProperties.setSimilarityThreshold(0.70);

        VectorRetriever vectorRetriever =
                new VectorRetriever(
                        embeddingService,
                        vectorStore,
                        ragProperties
                );

        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        "database connection",
                        1
                );

        assertFalse(results.isEmpty());

        assertEquals(
                "database-doc-chunk-0",
                results.get(0)
                        .getDocument()
                        .getId()
        );
    }
}