
package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreTest {

    private KnowledgeDocument createDocument(
            String id,
            String content,
            String sourceDocumentId
    ) {
        return KnowledgeDocument.builder()
                .id(id)
                .content(content)
                .metadata(Map.of(
                        "sourceDocumentId",
                        sourceDocumentId
                ))
                .build();
    }

    // ---------------------------------------------------------
    // 1. Basic insertion and retrieval
    // ---------------------------------------------------------

    @Test
    void shouldAddAndSearchDocument() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("PostgreSQL connection troubleshooting")
                        .build();

        float[] embedding = {0.1f, 0.2f, 0.3f};

        vectorStore.add(document, embedding);

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{0.1f, 0.2f, 0.3f},
                        1
                );

        assertNotNull(results);
        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0,
                results.get(0).getScore(),
                0.0001
        );
    }

    // ---------------------------------------------------------
    // 2. Invalid topK
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenTopKIsInvalid() {

        VectorStore vectorStore = new InMemoryVectorStore();

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{0.1f, 0.2f, 0.3f},
                        0
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ---------------------------------------------------------
    // 3. Similarity ranking
    // ---------------------------------------------------------

    @Test
    void shouldReturnMostSimilarDocumentFirst() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document1 =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("Database connection issue")
                        .build();

        KnowledgeDocument document2 =
                KnowledgeDocument.builder()
                        .id("doc-2")
                        .content("Kubernetes deployment issue")
                        .build();

        vectorStore.add(
                document1,
                new float[]{1.0f, 0.0f, 0.0f}
        );

        vectorStore.add(
                document2,
                new float[]{0.0f, 1.0f, 0.0f}
        );

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{0.9f, 0.1f, 0.0f},
                        2
                );

        assertEquals(2, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );

        assertTrue(
                results.get(0).getScore()
                        > results.get(1).getScore()
        );
    }

    // ---------------------------------------------------------
    // 4. Replace chunks belonging to a managed source
    // ---------------------------------------------------------

    @Test
    void shouldReplaceExistingChunksForManagedSource() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument oldDockerChunk1 =
                createDocument(
                        "docker-chunk-0",
                        "Old Docker content A",
                        "docker.md"
                );

        KnowledgeDocument oldDockerChunk2 =
                createDocument(
                        "docker-chunk-1",
                        "Old Docker content B",
                        "docker.md"
                );

        KnowledgeDocument postgresChunk =
                createDocument(
                        "postgres-chunk-0",
                        "PostgreSQL connection troubleshooting",
                        "postgres.md"
                );

        vectorStore.synchronize(
                List.of(
                        new EmbeddedDocument(
                                oldDockerChunk1,
                                new float[]{1.0f, 0.0f}
                        ),
                        new EmbeddedDocument(
                                oldDockerChunk2,
                                new float[]{0.8f, 0.2f}
                        ),
                        new EmbeddedDocument(
                                postgresChunk,
                                new float[]{0.0f, 1.0f}
                        )
                ),
                Set.of("docker.md", "postgres.md")
        );

        KnowledgeDocument updatedChunk1 =
                createDocument(
                        "docker-chunk-0",
                        "Updated Docker content A",
                        "docker.md"
                );

        KnowledgeDocument updatedChunk2 =
                createDocument(
                        "docker-chunk-1",
                        "Updated Docker content B",
                        "docker.md"
                );

        KnowledgeDocument updatedChunk3 =
                createDocument(
                        "docker-chunk-2",
                        "Updated Docker content C",
                        "docker.md"
                );

        vectorStore.synchronize(
                List.of(
                        new EmbeddedDocument(
                                updatedChunk1,
                                new float[]{1.0f, 0.0f}
                        ),
                        new EmbeddedDocument(
                                updatedChunk2,
                                new float[]{0.9f, 0.1f}
                        ),
                        new EmbeddedDocument(
                                updatedChunk3,
                                new float[]{0.7f, 0.3f}
                        )
                ),
                Set.of("docker.md")
        );

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                );

        assertEquals(4, results.size());

        Set<String> retrievedIds =
                results.stream()
                        .map(result -> result.getDocument().getId())
                        .collect(Collectors.toSet());

        assertTrue(retrievedIds.contains("docker-chunk-0"));
        assertTrue(retrievedIds.contains("docker-chunk-1"));
        assertTrue(retrievedIds.contains("docker-chunk-2"));
        assertTrue(retrievedIds.contains("postgres-chunk-0"));

        KnowledgeDocument retrievedDockerChunk =
                results.stream()
                        .map(RetrievalResult::getDocument)
                        .filter(doc -> doc.getId().equals("docker-chunk-0"))
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                "Updated Docker content A",
                retrievedDockerChunk.getContent()
        );

        assertFalse(
                results.stream()
                        .map(result -> result.getDocument().getContent())
                        .anyMatch(content ->
                                content.equals("Old Docker content A")
                                        || content.equals("Old Docker content B")
                        )
        );
    }

    // ---------------------------------------------------------
    // 5. Failed synchronization must preserve previous snapshot
    // ---------------------------------------------------------

    @Test
    void shouldPreservePreviousSnapshotWhenSynchronizationFails() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument oldDockerDocument =
                createDocument(
                        "docker-chunk-0",
                        "Old Docker content",
                        "docker.md"
                );

        KnowledgeDocument postgresDocument =
                createDocument(
                        "postgres-chunk-0",
                        "PostgreSQL content",
                        "postgres.md"
                );

        vectorStore.synchronize(
                List.of(
                        new EmbeddedDocument(
                                oldDockerDocument,
                                new float[]{1.0f, 0.0f}
                        ),
                        new EmbeddedDocument(
                                postgresDocument,
                                new float[]{0.0f, 1.0f}
                        )
                ),
                Set.of("docker.md", "postgres.md")
        );

        KnowledgeDocument newDockerDocument =
                createDocument(
                        "docker-chunk-1",
                        "New Docker content",
                        "docker.md"
                );

        KnowledgeDocument invalidDocument =
                createDocument(
                        "docker-chunk-2",
                        "Invalid Docker content",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.synchronize(
                        List.of(
                                new EmbeddedDocument(
                                        newDockerDocument,
                                        new float[]{1.0f, 0.0f}
                                ),
                                new EmbeddedDocument(
                                        invalidDocument,
                                        new float[]{Float.NaN, 0.0f}
                                )
                        ),
                        Set.of("docker.md")
                )
        );

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                );

        Set<String> retrievedIds =
                results.stream()
                        .map(result -> result.getDocument().getId())
                        .collect(Collectors.toSet());

        assertEquals(2, results.size());

        assertTrue(retrievedIds.contains("docker-chunk-0"));
        assertTrue(retrievedIds.contains("postgres-chunk-0"));

        assertFalse(retrievedIds.contains("docker-chunk-1"));
        assertFalse(retrievedIds.contains("docker-chunk-2"));
    }

    // ---------------------------------------------------------
    // 6. Duplicate chunk IDs must be rejected
    // ---------------------------------------------------------

    @Test
    void shouldRejectDuplicateChunkIds() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document1 =
                createDocument(
                        "docker-chunk-0",
                        "Docker content A",
                        "docker.md"
                );

        KnowledgeDocument document2 =
                createDocument(
                        "docker-chunk-0",
                        "Docker content B",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.synchronize(
                        List.of(
                                new EmbeddedDocument(
                                        document1,
                                        new float[]{1.0f, 0.0f}
                                ),
                                new EmbeddedDocument(
                                        document2,
                                        new float[]{0.0f, 1.0f}
                                )
                        ),
                        Set.of("docker.md")
                )
        );

        assertTrue(
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                ).isEmpty()
        );
    }

    // ---------------------------------------------------------
    // 7. Invalid embeddings must be rejected
    // ---------------------------------------------------------

    @Test
    void shouldRejectInvalidEmbeddings() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("Test document")
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.add(document, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.add(document, new float[]{})
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.add(
                        document,
                        new float[]{Float.NaN, 0.0f}
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.add(
                        document,
                        new float[]{Float.POSITIVE_INFINITY, 0.0f}
                )
        );

        assertTrue(
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                ).isEmpty()
        );
    }

    // ---------------------------------------------------------
    // 8. Unmanaged source documents must be rejected
    // ---------------------------------------------------------

    @Test
    void shouldRejectUnmanagedSourceDocuments() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument postgresDocument =
                createDocument(
                        "postgres-chunk-0",
                        "PostgreSQL content",
                        "postgres.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.synchronize(
                        List.of(
                                new EmbeddedDocument(
                                        postgresDocument,
                                        new float[]{1.0f, 0.0f}
                                )
                        ),
                        Set.of("docker.md")
                )
        );

        assertTrue(
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                ).isEmpty()
        );
    }

    // ---------------------------------------------------------
    // 9. Every managed source must have incoming chunks
    // ---------------------------------------------------------

    @Test
    void shouldRejectManagedSourceWithoutIncomingChunks() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument dockerDocument =
                createDocument(
                        "docker-chunk-0",
                        "Docker content",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.synchronize(
                        List.of(
                                new EmbeddedDocument(
                                        dockerDocument,
                                        new float[]{1.0f, 0.0f}
                                )
                        ),
                        Set.of("docker.md", "postgres.md")
                )
        );

        assertTrue(
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                ).isEmpty()
        );
    }

    // ---------------------------------------------------------
    // 10. Empty store should return empty results
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyResultsWhenStoreIsEmpty() {

        VectorStore vectorStore = new InMemoryVectorStore();

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        5
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ---------------------------------------------------------
    // 11. Original embedding array must not mutate stored vector
    // ---------------------------------------------------------

    @Test
    void shouldDefensivelyCopyEmbeddingArrays() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("Docker troubleshooting")
                        .build();

        float[] originalEmbedding = {1.0f, 0.0f};

        vectorStore.add(document, originalEmbedding);

        // Mutate the original array after insertion.
        originalEmbedding[0] = 0.0f;
        originalEmbedding[1] = 1.0f;

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        1
                );

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0,
                results.get(0).getScore(),
                0.0001
        );
    }

    // ---------------------------------------------------------
    // 12. Inconsistent dimensions in one synchronization batch
    // ---------------------------------------------------------

    @Test
    void shouldRejectInconsistentEmbeddingDimensions() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document1 =
                createDocument(
                        "docker-chunk-0",
                        "Docker content",
                        "docker.md"
                );

        KnowledgeDocument document2 =
                createDocument(
                        "docker-chunk-1",
                        "More Docker content",
                        "docker.md"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.synchronize(
                        List.of(
                                new EmbeddedDocument(
                                        document1,
                                        new float[]{1.0f, 0.0f}
                                ),
                                new EmbeddedDocument(
                                        document2,
                                        new float[]{1.0f, 0.0f, 0.0f}
                                )
                        ),
                        Set.of("docker.md")
                )
        );

        assertTrue(
                vectorStore.search(
                        new float[]{1.0f, 0.0f},
                        10
                ).isEmpty()
        );
    }

    // ---------------------------------------------------------
    // 13. Query dimension must match stored embedding dimension
    // ---------------------------------------------------------

    @Test
    void shouldRejectDimensionMismatchDuringSearch() {

        VectorStore vectorStore = new InMemoryVectorStore();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("Docker troubleshooting")
                        .build();

        vectorStore.add(
                document,
                new float[]{1.0f, 0.0f}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> vectorStore.search(
                        new float[]{1.0f, 0.0f, 0.0f},
                        1
                )
        );
    }

    // ---------------------------------------------------------
    // 14. topK must limit the number of returned results
    // ---------------------------------------------------------

    @Test
    void shouldRespectTopK() {

        VectorStore vectorStore = new InMemoryVectorStore();

        for (int i = 1; i <= 3; i++) {

            KnowledgeDocument document =
                    KnowledgeDocument.builder()
                            .id("doc-" + i)
                            .content("Document " + i)
                            .build();

            vectorStore.add(
                    document,
                    new float[]{
                            (float) i,
                            0.0f,
                            0.0f
                    }
            );
        }

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{1.0f, 0.0f, 0.0f},
                        2
                );

        assertEquals(2, results.size());
    }
}
