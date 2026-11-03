
package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BM25IndexTest {

    private BM25Index bm25Index;

    @BeforeEach
    void setUp() {
        bm25Index = new BM25Index();
    }

    // --------------------------------------------------
    // 1. INDEX LIFECYCLE TESTS
    // --------------------------------------------------

    @Test
    void rebuildShouldPreserveOldIndexWhenNewDocumentIsInvalid() {

        KnowledgeDocument original = createDocument(
                "original-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(original));

        KnowledgeDocument invalid = createDocument(
                "invalid-1",
                "   "
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(List.of(invalid))
        );

        List<RetrievalResult> results =
                bm25Index.search("Docker connection", 5);

        assertFalse(results.isEmpty());

        assertEquals(
                "original-1",
                results.get(0).getDocument().getId()
        );
    }

    @Test
    void rebuildShouldReplaceOldIndexWithNewIndex() {

        KnowledgeDocument original = createDocument(
                "original-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(original));

        KnowledgeDocument replacement = createDocument(
                "replacement-1",
                "PostgreSQL database authentication failure"
        );

        bm25Index.rebuild(List.of(replacement));

        List<RetrievalResult> newResults =
                bm25Index.search("PostgreSQL authentication", 5);

        assertFalse(newResults.isEmpty());

        assertEquals(
                "replacement-1",
                newResults.get(0).getDocument().getId()
        );

        List<RetrievalResult> oldResults =
                bm25Index.search("Docker connection", 5);

        assertTrue(oldResults.isEmpty());
    }

    @Test
    void rebuildShouldAllowEmptyDocumentList() {

        KnowledgeDocument original = createDocument(
                "original-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(original));

        bm25Index.rebuild(List.of());

        List<RetrievalResult> results =
                bm25Index.search("Docker connection", 5);

        assertTrue(results.isEmpty());
    }

    // --------------------------------------------------
    // 2. INPUT VALIDATION TESTS
    // --------------------------------------------------

    @Test
    void shouldRejectNullDocumentList() {

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(null)
        );
    }

    @Test
    void shouldRejectNullDocument() {

        List<KnowledgeDocument> documents =
                java.util.Arrays.asList(
                        createDocument(
                                "valid-1",
                                "Docker container connection"
                        ),
                        null
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(documents)
        );
    }

    @Test
    void shouldRejectBlankDocumentId() {

        KnowledgeDocument invalid = createDocument(
                "   ",
                "Docker container connection"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(List.of(invalid))
        );
    }

    @Test
    void shouldRejectBlankDocumentContent() {

        KnowledgeDocument invalid = createDocument(
                "invalid-1",
                "   "
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(List.of(invalid))
        );
    }

    @Test
    void shouldRejectDuplicateDocumentIds() {

        KnowledgeDocument first = createDocument(
                "duplicate-1",
                "Docker container connection refused"
        );

        KnowledgeDocument second = createDocument(
                "duplicate-1",
                "PostgreSQL database authentication failure"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> bm25Index.rebuild(
                        List.of(first, second)
                )
        );
    }

    // --------------------------------------------------
    // 3. SEARCH BEHAVIOR TESTS
    // --------------------------------------------------

    @Test
    void shouldReturnEmptyForUnmatchedQuery() {

        KnowledgeDocument document = createDocument(
                "docker-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(document));

        List<RetrievalResult> results =
                bm25Index.search("PostgreSQL database", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRankMoreRelevantDocumentHigher() {

        KnowledgeDocument weakMatch = createDocument(
                "weak-1",
                "Docker image pull failed"
        );

        KnowledgeDocument strongMatch = createDocument(
                "strong-1",
                "Docker container keeps restarting because the application crashes"
        );

        bm25Index.rebuild(
                List.of(weakMatch, strongMatch)
        );

        List<RetrievalResult> results =
                bm25Index.search(
                        "Docker container keeps restarting",
                        5
                );

        assertFalse(results.isEmpty());

        assertEquals(
                "strong-1",
                results.get(0).getDocument().getId()
        );

        if (results.size() > 1) {
            assertTrue(
                    results.get(0).getScore()
                            > results.get(1).getScore()
            );
        }
    }

    @Test
    void shouldReturnDeterministicOrderForEqualScores() {

        KnowledgeDocument documentB = createDocument(
                "document-b",
                "Docker container failure"
        );

        KnowledgeDocument documentA = createDocument(
                "document-a",
                "Docker container failure"
        );

        bm25Index.rebuild(
                List.of(documentB, documentA)
        );

        List<RetrievalResult> firstSearch =
                bm25Index.search("Docker container", 5);

        List<RetrievalResult> secondSearch =
                bm25Index.search("Docker container", 5);

        assertEquals(2, firstSearch.size());
        assertEquals(2, secondSearch.size());

        assertEquals(
                firstSearch.get(0).getDocument().getId(),
                secondSearch.get(0).getDocument().getId()
        );

        assertEquals(
                firstSearch.get(1).getDocument().getId(),
                secondSearch.get(1).getDocument().getId()
        );

        // Equal scores are ordered lexicographically by ID.
        assertEquals(
                "document-a",
                firstSearch.get(0).getDocument().getId()
        );

        assertEquals(
                "document-b",
                firstSearch.get(1).getDocument().getId()
        );
    }

    // --------------------------------------------------
    // 4. PREPARE AND PUBLISH TESTS
    // --------------------------------------------------

    @Test
    void shouldPreserveActiveIndexUntilPreparedIndexIsPublished() {

        KnowledgeDocument original = createDocument(
                "original-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(original));

        KnowledgeDocument replacement = createDocument(
                "replacement-1",
                "PostgreSQL database authentication failure"
        );

        // Prepare a candidate without publishing it.
        BM25Index.PreparedIndex preparedIndex =
                bm25Index.prepare(List.of(replacement));

        // Active index must still contain original document.
        List<RetrievalResult> oldResults =
                bm25Index.search("Docker connection", 5);

        assertFalse(oldResults.isEmpty());

        assertEquals(
                "original-1",
                oldResults.get(0).getDocument().getId()
        );

        // New document must not be searchable yet.
        List<RetrievalResult> newResultsBeforePublish =
                bm25Index.search("PostgreSQL authentication", 5);

        assertTrue(newResultsBeforePublish.isEmpty());

        // Publish the prepared candidate.
        bm25Index.publish(preparedIndex);

        // Now the replacement should be searchable.
        List<RetrievalResult> newResultsAfterPublish =
                bm25Index.search("PostgreSQL authentication", 5);

        assertFalse(newResultsAfterPublish.isEmpty());

        assertEquals(
                "replacement-1",
                newResultsAfterPublish.get(0)
                        .getDocument()
                        .getId()
        );

        // Original document should no longer be searchable.
        List<RetrievalResult> oldResultsAfterPublish =
                bm25Index.search("Docker connection", 5);

        assertTrue(oldResultsAfterPublish.isEmpty());
    }

    // --------------------------------------------------
    // 5. SEARCH INPUT EDGE CASES
    // --------------------------------------------------

    @Test
    void shouldReturnEmptyForInvalidSearchInput() {

        KnowledgeDocument document = createDocument(
                "docker-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(document));

        assertTrue(
                bm25Index.search(null, 5).isEmpty()
        );

        assertTrue(
                bm25Index.search("", 5).isEmpty()
        );

        assertTrue(
                bm25Index.search("   ", 5).isEmpty()
        );

        assertTrue(
                bm25Index.search("Docker", 0).isEmpty()
        );

        assertTrue(
                bm25Index.search("Docker", -1).isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyForQueryContainingOnlySpecialCharacters() {

        KnowledgeDocument document = createDocument(
                "docker-1",
                "Docker container connection refused"
        );

        bm25Index.rebuild(List.of(document));

        List<RetrievalResult> results =
                bm25Index.search("!@#$%^&*()", 5);

        assertTrue(results.isEmpty());
    }

    // --------------------------------------------------
    // 6. TOP K TEST
    // --------------------------------------------------

    @Test
    void shouldRespectTopK() {

        KnowledgeDocument first = createDocument(
                "docker-1",
                "Docker container connection refused"
        );

        KnowledgeDocument second = createDocument(
                "docker-2",
                "Docker container startup failure"
        );

        KnowledgeDocument third = createDocument(
                "docker-3",
                "Docker container networking problem"
        );

        bm25Index.rebuild(
                List.of(first, second, third)
        );

        List<RetrievalResult> results =
                bm25Index.search("Docker container", 2);

        assertEquals(2, results.size());
    }

    // --------------------------------------------------
    // HELPER
    // --------------------------------------------------

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
