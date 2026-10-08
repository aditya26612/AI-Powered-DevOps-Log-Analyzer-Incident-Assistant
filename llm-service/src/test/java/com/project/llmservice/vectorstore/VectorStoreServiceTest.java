package com.project.llmservice.vectorstore;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {

        vectorStoreService =
                new VectorStoreService(
                        embeddingService,
                        vectorStore
                );
    }

    @Test
    void shouldGenerateEmbeddingAndStoreDocument() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content(
                                "PostgreSQL connection troubleshooting"
                        )
                        .build();

        float[] embedding = {
                0.1f,
                0.2f,
                0.3f
        };

        when(embeddingService.embed(
                document.getContent()
        )).thenReturn(embedding);

        vectorStoreService.addDocument(document);

        verify(embeddingService)
                .embed(document.getContent());

        verify(vectorStore)
                .add(document, embedding);
    }

    @Test
    void shouldGenerateQueryEmbeddingAndSearchVectorStore() {

        String query =
                "PostgreSQL connection failed";

        float[] queryEmbedding = {
                0.1f,
                0.2f,
                0.3f
        };

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content(
                                "Database troubleshooting"
                        )
                        .build();

        RetrievalResult retrievalResult =
                RetrievalResult.builder()
                        .document(document)
                        .score(0.95)
                        .build();

        when(embeddingService.embed(query))
                .thenReturn(queryEmbedding);

        when(vectorStore.search(
                queryEmbedding,
                3
        )).thenReturn(List.of(retrievalResult));

        List<RetrievalResult> results =
                vectorStoreService.search(
                        query,
                        3
                );

        assertNotNull(results);
        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0)
                        .getDocument()
                        .getId()
        );

        assertEquals(
                0.95,
                results.get(0).getScore()
        );

        verify(embeddingService)
                .embed(query);

        verify(vectorStore)
                .search(queryEmbedding, 3);
    }

    @Test
    void shouldReturnEmptyListForInvalidQuery() {

        List<RetrievalResult> results =
                vectorStoreService.search(
                        "",
                        3
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldReturnEmptyListForInvalidTopK() {

        List<RetrievalResult> results =
                vectorStoreService.search(
                        "database error",
                        0
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorStore);
    }
}