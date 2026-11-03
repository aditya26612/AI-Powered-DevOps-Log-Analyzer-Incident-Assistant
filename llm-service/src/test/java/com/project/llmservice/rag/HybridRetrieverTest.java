
package com.project.llmservice.rag;

import com.project.llmservice.rag.bm25.BM25Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridRetrieverTest {

    private VectorRetriever vectorRetriever;
    private BM25Retriever bm25Retriever;
    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {

        vectorRetriever = mock(VectorRetriever.class);
        bm25Retriever = mock(BM25Retriever.class);

        hybridRetriever = new HybridRetriever(
                vectorRetriever,
                bm25Retriever
        );
    }

    // ---------------------------------------------------------
    // Input validation tests
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyForNullQuery() {

        List<RetrievalResult> results =
                hybridRetriever.retrieve(null, 5);

        assertTrue(results.isEmpty());

        verifyNoInteractions(
                vectorRetriever,
                bm25Retriever
        );
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {

        List<RetrievalResult> results =
                hybridRetriever.retrieve("   ", 5);

        assertTrue(results.isEmpty());

        verifyNoInteractions(
                vectorRetriever,
                bm25Retriever
        );
    }

    @Test
    void shouldReturnEmptyForInvalidTopK() {

        assertTrue(
                hybridRetriever.retrieve("docker", 0).isEmpty()
        );

        assertTrue(
                hybridRetriever.retrieve("docker", -1).isEmpty()
        );

        verifyNoInteractions(
                vectorRetriever,
                bm25Retriever
        );
    }

    // ---------------------------------------------------------
    // Basic retrieval tests
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyWhenBothRetrieversReturnEmpty() {

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of());

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnDenseResultsWhenBm25ReturnsEmpty() {

        KnowledgeDocument document =
                createDocument(
                        "docker-doc",
                        "Docker container networking"
                );

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(document, 0.95)
                ));

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        assertEquals(1, results.size());

        assertEquals(
                "docker-doc",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0 / 61,
                results.get(0).getScore(),
                0.000001
        );
    }

    @Test
    void shouldReturnBm25ResultsWhenDenseReturnsEmpty() {

        KnowledgeDocument document =
                createDocument(
                        "kubernetes-doc",
                        "Kubernetes pod scheduling"
                );

        when(vectorRetriever.retrieve("kubernetes", 5))
                .thenReturn(List.of());

        when(bm25Retriever.retrieve("kubernetes", 5))
                .thenReturn(List.of(
                        createResult(document, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("kubernetes", 5);

        assertEquals(1, results.size());

        assertEquals(
                "kubernetes-doc",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0 / 61,
                results.get(0).getScore(),
                0.000001
        );
    }

    @Test
    void shouldCombineScoresForDocumentFoundByBothRetrievers() {

        KnowledgeDocument sharedDocument =
                createDocument(
                        "shared-doc",
                        "Docker container networking"
                );

        KnowledgeDocument denseOnlyDocument =
                createDocument(
                        "dense-doc",
                        "Docker container runtime"
                );

        KnowledgeDocument bm25OnlyDocument =
                createDocument(
                        "bm25-doc",
                        "Docker network configuration"
                );

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(sharedDocument, 0.95),
                        createResult(denseOnlyDocument, 0.85)
                ));

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(sharedDocument, 12.0),
                        createResult(bm25OnlyDocument, 9.0)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        assertEquals(3, results.size());

        RetrievalResult sharedResult =
                findById(results, "shared-doc");

        assertNotNull(sharedResult);

        double expectedScore =
                (1.0 / 61) + (1.0 / 61);

        assertEquals(
                expectedScore,
                sharedResult.getScore(),
                0.000001
        );

        assertEquals(
                "shared-doc",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldSortEqualScoresByDocumentId() {

        KnowledgeDocument docA =
                createDocument(
                        "doc-a",
                        "Docker networking"
                );

        KnowledgeDocument docB =
                createDocument(
                        "doc-b",
                        "Kubernetes scheduling"
                );

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(docB, 0.9)
                ));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(docA, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(2, results.size());

        assertEquals(
                1.0 / 61,
                results.get(0).getScore(),
                0.000001
        );

        assertEquals(
                1.0 / 61,
                results.get(1).getScore(),
                0.000001
        );

        assertEquals(
                List.of("doc-a", "doc-b"),
                results.stream()
                        .map(result -> result.getDocument().getId())
                        .toList()
        );
    }

    @Test
    void shouldRespectTopK() {

        KnowledgeDocument document1 =
                createDocument("doc-1", "Docker networking");

        KnowledgeDocument document2 =
                createDocument("doc-2", "Docker containers");

        KnowledgeDocument document3 =
                createDocument("doc-3", "Docker deployment");

        when(vectorRetriever.retrieve("docker", 2))
                .thenReturn(List.of(
                        createResult(document1, 0.95),
                        createResult(document2, 0.90),
                        createResult(document3, 0.85)
                ));

        when(bm25Retriever.retrieve("docker", 2))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 2);

        assertEquals(2, results.size());
    }

    // ---------------------------------------------------------
    // Candidate depth tests
    // ---------------------------------------------------------

    @Test
    void shouldRequestExactlyTopKCandidatesFromEachRetriever() {

        when(vectorRetriever.retrieve("docker", 3))
                .thenReturn(List.of());

        when(bm25Retriever.retrieve("docker", 3))
                .thenReturn(List.of());

        hybridRetriever.retrieve("docker", 3);

        verify(vectorRetriever)
                .retrieve("docker", 3);

        verify(bm25Retriever)
                .retrieve("docker", 3);
    }

    @Test
    void shouldRequestExactlyTopKWhenTopKExceedsFive() {

        when(vectorRetriever.retrieve("docker", 10))
                .thenReturn(List.of());

        when(bm25Retriever.retrieve("docker", 10))
                .thenReturn(List.of());

        hybridRetriever.retrieve("docker", 10);

        verify(vectorRetriever)
                .retrieve("docker", 10);

        verify(bm25Retriever)
                .retrieve("docker", 10);
    }

    // ---------------------------------------------------------
    // Null and invalid result handling
    // ---------------------------------------------------------

    @Test
    void shouldHandleNullDenseResultList() {

        KnowledgeDocument document =
                createDocument("doc-1", "Docker networking");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(null);

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(document, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldHandleNullBm25ResultList() {

        KnowledgeDocument document =
                createDocument("doc-1", "Docker networking");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(document, 0.9)
                ));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(null);

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldSkipNullResults() {

        KnowledgeDocument document =
                createDocument("doc-1", "Docker networking");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(Arrays.asList(
                        null,
                        createResult(document, 0.9)
                ));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldSkipResultsWithNullDocuments() {

        RetrievalResult invalidResult =
                RetrievalResult.builder()
                        .document(null)
                        .score(0.9)
                        .build();

        KnowledgeDocument validDocument =
                createDocument("doc-1", "Docker networking");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(List.of(invalidResult));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(validDocument, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldSkipDocumentsWithNullIds() {

        KnowledgeDocument invalidDocument =
                createDocument(null, "Docker networking");

        KnowledgeDocument validDocument =
                createDocument("doc-1", "Kubernetes scheduling");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(invalidDocument, 0.9)
                ));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(validDocument, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void shouldSkipDocumentsWithBlankIds() {

        KnowledgeDocument invalidDocument =
                createDocument("   ", "Docker networking");

        KnowledgeDocument validDocument =
                createDocument("doc-1", "Kubernetes scheduling");

        when(vectorRetriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(invalidDocument, 0.9)
                ));

        when(bm25Retriever.retrieve("query", 2))
                .thenReturn(List.of(
                        createResult(validDocument, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 2);

        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0).getDocument().getId()
        );
    }

    // ---------------------------------------------------------
    // Duplicate source-document handling
    // ---------------------------------------------------------

    @Test
    void shouldCountOnlyFirstOccurrenceOfDenseDuplicate() {

        KnowledgeDocument documentA =
                createDocument("doc-A", "Docker networking");

        KnowledgeDocument documentB =
                createDocument("doc-B", "Docker containers");

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(documentA, 0.95),
                        createResult(documentA, 0.90),
                        createResult(documentB, 0.85)
                ));

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        RetrievalResult resultA =
                findById(results, "doc-A");

        assertNotNull(resultA);

        assertEquals(
                1.0 / 61,
                resultA.getScore(),
                0.000001
        );

        assertEquals(2, results.size());
    }

    @Test
    void shouldCountOnlyFirstOccurrenceOfBm25Duplicate() {

        KnowledgeDocument documentA =
                createDocument("doc-A", "Docker networking");

        KnowledgeDocument documentB =
                createDocument("doc-B", "Docker containers");

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of());

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(documentA, 12.0),
                        createResult(documentA, 10.0),
                        createResult(documentB, 8.0)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        RetrievalResult resultA =
                findById(results, "doc-A");

        assertNotNull(resultA);

        assertEquals(
                1.0 / 61,
                resultA.getScore(),
                0.000001
        );

        assertEquals(2, results.size());
    }

    @Test
    void shouldAggregateDifferentChunksFromSameSourceDocument() {

        KnowledgeDocument chunk0 =
                createChunk(
                        "docker-networking-chunk-0",
                        "docker-networking",
                        "0",
                        "Docker networking overview"
                );

        KnowledgeDocument chunk1 =
                createChunk(
                        "docker-networking-chunk-1",
                        "docker-networking",
                        "1",
                        "Docker bridge networking"
                );

        KnowledgeDocument anotherDocument =
                createDocument(
                        "kubernetes-doc",
                        "Kubernetes networking"
                );

        when(vectorRetriever.retrieve("docker", 5))
                .thenReturn(List.of(
                        createResult(chunk0, 0.95),
                        createResult(chunk1, 0.90),
                        createResult(anotherDocument, 0.85)
                ));

        when(bm25Retriever.retrieve("docker", 5))
                .thenReturn(List.of());

        List<RetrievalResult> results =
                hybridRetriever.retrieve("docker", 5);

        // Two different chunks from one source count as one document.
        assertEquals(2, results.size());

        RetrievalResult aggregatedResult =
                findById(results, "docker-networking-chunk-0");

        assertNotNull(aggregatedResult);

        // The second chunk must not add another RRF contribution.
        assertEquals(
                1.0 / 61,
                aggregatedResult.getScore(),
                0.000001
        );
    }

    @Test
    void shouldFuseDifferentChunksFromSameSourceAcrossRetrievers() {

        KnowledgeDocument denseChunk =
                createChunk(
                        "kubernetes-crashloopbackoff-chunk-0",
                        "kubernetes-crashloopbackoff",
                        "0",
                        "CrashLoopBackOff overview"
                );

        KnowledgeDocument bm25Chunk =
                createChunk(
                        "kubernetes-crashloopbackoff-chunk-1",
                        "kubernetes-crashloopbackoff",
                        "1",
                        "CrashLoopBackOff troubleshooting"
                );

        when(vectorRetriever.retrieve("kubernetes", 5))
                .thenReturn(List.of(
                        createResult(denseChunk, 0.95)
                ));

        when(bm25Retriever.retrieve("kubernetes", 5))
                .thenReturn(List.of(
                        createResult(bm25Chunk, 12.0)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("kubernetes", 5);

        // Both chunks represent one source document.
        assertEquals(1, results.size());

        // Each retriever contributes once.
        assertEquals(
                (1.0 / 61) + (1.0 / 61),
                results.get(0).getScore(),
                0.000001
        );

        // Dense is the deterministic representative when ranks tie.
        assertEquals(
                "kubernetes-crashloopbackoff-chunk-0",
                results.get(0).getDocument().getId()
        );
    }

    // ---------------------------------------------------------
    // Retriever failure isolation
    // ---------------------------------------------------------

    @Test
    void shouldReturnBm25ResultsWhenDenseRetrieverThrowsException() {

        KnowledgeDocument document =
                createDocument(
                        "bm25-doc",
                        "Kubernetes pod scheduling"
                );

        when(vectorRetriever.retrieve("query", 5))
                .thenThrow(
                        new RuntimeException("Vector retrieval failed")
                );

        when(bm25Retriever.retrieve("query", 5))
                .thenReturn(List.of(
                        createResult(document, 8.5)
                ));

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 5);

        assertEquals(1, results.size());

        assertEquals(
                "bm25-doc",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0 / 61,
                results.get(0).getScore(),
                0.000001
        );

        verify(vectorRetriever).retrieve("query", 5);
        verify(bm25Retriever).retrieve("query", 5);
    }

    @Test
    void shouldReturnDenseResultsWhenBm25RetrieverThrowsException() {

        KnowledgeDocument document =
                createDocument(
                        "dense-doc",
                        "Docker container networking"
                );

        when(vectorRetriever.retrieve("query", 5))
                .thenReturn(List.of(
                        createResult(document, 0.95)
                ));

        when(bm25Retriever.retrieve("query", 5))
                .thenThrow(
                        new RuntimeException("BM25 retrieval failed")
                );

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 5);

        assertEquals(1, results.size());

        assertEquals(
                "dense-doc",
                results.get(0).getDocument().getId()
        );

        assertEquals(
                1.0 / 61,
                results.get(0).getScore(),
                0.000001
        );

        verify(vectorRetriever).retrieve("query", 5);
        verify(bm25Retriever).retrieve("query", 5);
    }

    @Test
    void shouldReturnEmptyWhenBothRetrieversThrowExceptions() {

        when(vectorRetriever.retrieve("query", 5))
                .thenThrow(
                        new RuntimeException("Vector retrieval failed")
                );

        when(bm25Retriever.retrieve("query", 5))
                .thenThrow(
                        new RuntimeException("BM25 retrieval failed")
                );

        List<RetrievalResult> results =
                hybridRetriever.retrieve("query", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(vectorRetriever).retrieve("query", 5);
        verify(bm25Retriever).retrieve("query", 5);
    }

    // ---------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------

    private KnowledgeDocument createDocument(
            String id,
            String content
    ) {

        return KnowledgeDocument.builder()
                .id(id)
                .content(content)
                .metadata(Map.of())
                .build();
    }

    private KnowledgeDocument createChunk(
            String chunkId,
            String sourceDocumentId,
            String chunkIndex,
            String content
    ) {

        return KnowledgeDocument.builder()
                .id(chunkId)
                .content(content)
                .metadata(Map.of(
                        "sourceDocumentId", sourceDocumentId,
                        "chunkIndex", chunkIndex
                ))
                .build();
    }

    private RetrievalResult createResult(
            KnowledgeDocument document,
            double score
    ) {

        return RetrievalResult.builder()
                .document(document)
                .score(score)
                .build();
    }

    private RetrievalResult findById(
            List<RetrievalResult> results,
            String documentId
    ) {

        return results.stream()
                .filter(result ->
                        result.getDocument()
                                .getId()
                                .equals(documentId)
                )
                .findFirst()
                .orElse(null);
    }
}
