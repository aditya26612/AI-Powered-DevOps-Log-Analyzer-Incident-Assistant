package com.project.llmservice.vectorstore;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemoryVectorStoreTest {

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

    // --------------------------------------------------
    // EXISTING SINGLE-DOCUMENT TEST
    // --------------------------------------------------

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

    // --------------------------------------------------
    // EXISTING SEARCH TEST
    // --------------------------------------------------

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

    // --------------------------------------------------
    // EXISTING INVALID SEARCH TESTS
    // --------------------------------------------------

    @Test
    void shouldReturnEmptyListForInvalidQuery() {

        List<RetrievalResult> results =
                vectorStoreService.search("", 3);

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

    // --------------------------------------------------
    // BULK SYNCHRONIZATION TESTS
    // --------------------------------------------------

    @Test
    void shouldGenerateAllEmbeddingsAndSynchronizeDocuments() {

        KnowledgeDocument dockerChunk =
                createDocument(
                        "docker-chunk-0",
                        "Docker container startup failure",
                        "docker.md"
                );

        KnowledgeDocument postgresChunk =
                createDocument(
                        "postgres-chunk-0",
                        "PostgreSQL connection refused",
                        "postgres.md"
                );

        float[] dockerEmbedding = {
                0.1f, 0.2f, 0.3f
        };

        float[] postgresEmbedding = {
                0.4f, 0.5f, 0.6f
        };

        when(embeddingService.embed(
                dockerChunk.getContent()
        )).thenReturn(dockerEmbedding);

        when(embeddingService.embed(
                postgresChunk.getContent()
        )).thenReturn(postgresEmbedding);

        vectorStoreService.synchronizeDocuments(
                List.of(dockerChunk, postgresChunk),
                Set.of("docker.md", "postgres.md")
        );

        ArgumentCaptor<List<EmbeddedDocument>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(vectorStore).synchronize(
                captor.capture(),
                eq(Set.of("docker.md", "postgres.md"))
        );

        List<EmbeddedDocument> embeddedDocuments =
                captor.getValue();

        assertEquals(2, embeddedDocuments.size());

        assertEquals(
                "docker-chunk-0",
                embeddedDocuments.get(0).document().getId()
        );

        assertArrayEquals(
                dockerEmbedding,
                embeddedDocuments.get(0).embedding()
        );

        assertEquals(
                "postgres-chunk-0",
                embeddedDocuments.get(1).document().getId()
        );

        assertArrayEquals(
                postgresEmbedding,
                embeddedDocuments.get(1).embedding()
        );

        // Verify embedding generation happens before synchronization.
        InOrder inOrder =
                inOrder(embeddingService, vectorStore);

        inOrder.verify(embeddingService)
                .embed(dockerChunk.getContent());

        inOrder.verify(embeddingService)
                .embed(postgresChunk.getContent());

        inOrder.verify(vectorStore)
                .synchronize(
                        anyList(),
                        eq(Set.of("docker.md", "postgres.md"))
                );
    }

    @Test
    void shouldNotSynchronizeIfEmbeddingGenerationFails() {

        KnowledgeDocument firstChunk =
                createDocument(
                        "docker-chunk-0",
                        "Docker startup failure",
                        "docker.md"
                );

        KnowledgeDocument secondChunk =
                createDocument(
                        "docker-chunk-1",
                        "Docker networking failure",
                        "docker.md"
                );

        when(embeddingService.embed(
                firstChunk.getContent()
        )).thenReturn(new float[]{0.1f, 0.2f});

        when(embeddingService.embed(
                secondChunk.getContent()
        )).thenThrow(
                new RuntimeException("Embedding service failed")
        );

        assertThrows(
                RuntimeException.class,
                () -> vectorStoreService.synchronizeDocuments(
                        List.of(firstChunk, secondChunk),
                        Set.of("docker.md")
                )
        );

        // No partial synchronization is allowed.
        verify(vectorStore, never())
                .synchronize(anyList(), anySet());
    }

    @Test
    void shouldRejectDuplicateChunkIdsBeforeEmbedding() {

        KnowledgeDocument firstChunk =
                createDocument(
                        "docker-chunk-0",
                        "Content A",
                        "docker.md"
                );

        KnowledgeDocument secondChunk =
                createDocument(
                        "docker-chunk-0",
                        "Content B",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStoreService.synchronizeDocuments(
                        List.of(firstChunk, secondChunk),
                        Set.of("docker.md")
                )
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldRejectMissingManagedSourceBeforeEmbedding() {

        KnowledgeDocument document =
                createDocument(
                        "docker-chunk-0",
                        "Docker troubleshooting",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStoreService.synchronizeDocuments(
                        List.of(document),
                        Set.of("docker.md", "postgres.md")
                )
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldRejectEmptyChunkList() {

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStoreService.synchronizeDocuments(
                        List.of(),
                        Set.of("docker.md")
                )
        );

        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorStore);
    }

    // --------------------------------------------------
    // HELPER
    // --------------------------------------------------

    private KnowledgeDocument createDocument(
            String id,
            String content,
            String sourceDocumentId
    ) {

        return KnowledgeDocument.builder()
                .id(id)
                .content(content)
                .metadata(
                        Map.of(
                                "sourceDocumentId",
                                sourceDocumentId
                        )
                )
                .build();
    }
}
