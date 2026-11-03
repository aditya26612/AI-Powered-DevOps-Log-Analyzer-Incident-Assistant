
package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BM25RetrieverTest {

    private BM25Index bm25Index;
    private BM25Retriever bm25Retriever;

    @BeforeEach
    void setUp() {
        bm25Index = new BM25Index();
        bm25Retriever = new BM25Retriever(bm25Index);
    }

    @Test
    void shouldReturnEmptyBeforeInitialization() {

        List<RetrievalResult> results =
                bm25Retriever.retrieve("docker container", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnEmptyForInvalidQueryAndTopK() {

        bm25Retriever.rebuildIndex(List.of(
                createDocument("doc-1", "Docker container orchestration")
        ));

        assertTrue(
                bm25Retriever.retrieve(null, 5).isEmpty()
        );

        assertTrue(
                bm25Retriever.retrieve("   ", 5).isEmpty()
        );

        assertTrue(
                bm25Retriever.retrieve("docker", 0).isEmpty()
        );

        assertTrue(
                bm25Retriever.retrieve("docker", -1).isEmpty()
        );
    }

    @Test
    void prepareIndexShouldNotChangeActiveIndex() {

        bm25Retriever.rebuildIndex(List.of(
                createDocument("old-doc", "PostgreSQL database indexing")
        ));

        BM25Index.PreparedIndex preparedIndex =
                bm25Retriever.prepareIndex(List.of(
                        createDocument("new-doc", "Kubernetes pod scheduling")
                ));

        // Preparing should not publish the candidate.
        assertFalse(
                bm25Retriever.retrieve("kubernetes", 5).size() > 0
        );

        // The old active index should remain available.
        assertFalse(
                bm25Retriever.retrieve("postgresql", 5).isEmpty()
        );

        assertEquals(1, preparedIndex.size());
    }

    @Test
    void publishIndexShouldActivatePreparedIndex() {

        BM25Index.PreparedIndex preparedIndex =
                bm25Retriever.prepareIndex(List.of(
                        createDocument("doc-1", "Docker container networking")
                ));

        bm25Retriever.publishIndex(preparedIndex);

        List<RetrievalResult> results =
                bm25Retriever.retrieve("docker networking", 5);

        assertFalse(results.isEmpty());
    }

    @Test
    void shouldRejectNullPreparedIndex() {

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Retriever.publishIndex(null)
        );
    }

    @Test
    void shouldRejectNullInputDuringPreparation() {

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Retriever.prepareIndex(null)
        );
    }

    @Test
    void shouldClearActiveIndexWhenPublishedWithEmptyList() {

        bm25Retriever.rebuildIndex(List.of(
                createDocument("doc-1", "Docker container networking")
        ));

        assertFalse(
                bm25Retriever.retrieve("docker", 5).isEmpty()
        );

        bm25Retriever.rebuildIndex(List.of());

        assertTrue(
                bm25Retriever.retrieve("docker", 5).isEmpty()
        );
    }

    @Test
    void failedPreparationShouldPreserveActiveIndex() {

        bm25Retriever.rebuildIndex(List.of(
                createDocument("valid-doc", "PostgreSQL database indexing")
        ));

        KnowledgeDocument invalidDocument =
                createDocument("invalid-doc", "   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Retriever.rebuildIndex(
                        List.of(invalidDocument)
                )
        );

        // Previously published index must remain usable.
        assertFalse(
                bm25Retriever.retrieve("postgresql", 5).isEmpty()
        );
    }

    @Test
    void rebuildIndexShouldPublishNewIndex() {

        bm25Retriever.rebuildIndex(List.of(
                createDocument("old-doc", "Docker container networking")
        ));

        bm25Retriever.rebuildIndex(List.of(
                createDocument("new-doc", "Kubernetes pod scheduling")
        ));

        assertTrue(
                bm25Retriever.retrieve("docker", 5).isEmpty()
        );

        assertFalse(
                bm25Retriever.retrieve("kubernetes", 5).isEmpty()
        );
    }

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
}
