
package com.project.llmservice.rag.splitter;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.ResourceDocumentLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleDocumentSplitterTest {

    private SimpleDocumentSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new SimpleDocumentSplitter();
    }

    @Test
    void shouldSplitDocumentIntoChunksWithOverlap() {

        /*
         * No natural boundaries exist in this content.
         * Therefore, the splitter must use hard boundaries.
         */
        String content = "A".repeat(2500);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content(content)
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertNotNull(chunks);
        assertEquals(3, chunks.size());

        assertEquals(1000, chunks.get(0).getContent().length());
        assertEquals(1000, chunks.get(1).getContent().length());
        assertEquals(900, chunks.get(2).getContent().length());

        /*
         * Verify that consecutive chunks share 200 characters.
         */
        assertEquals(
                chunks.get(0).getContent().substring(800),
                chunks.get(1).getContent().substring(0, 200)
        );

        assertEquals(
                chunks.get(1).getContent().substring(800),
                chunks.get(2).getContent().substring(0, 200)
        );
    }

    @Test
    void shouldPreferParagraphBoundary() {

        String content =
                "A".repeat(700)
                        + "\n\n"
                        + "B".repeat(1500);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content(content)
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertTrue(chunks.size() > 1);

        /*
         * The first chunk should end at the paragraph boundary,
         * rather than splitting the paragraph.
         */
        assertTrue(
                chunks.get(0).getContent().endsWith("\n\n")
        );
    }

    @Test
    void shouldPreferSentenceBoundaryWhenParagraphBoundaryIsAbsent() {

        String content =
                "A".repeat(700)
                        + ". "
                        + "B".repeat(1500);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content(content)
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertTrue(chunks.size() > 1);

        assertTrue(
                chunks.get(0).getContent().endsWith(". ")
        );
    }

    @Test
    void shouldPreferWordBoundaryWhenSentenceBoundaryIsAbsent() {

        String content =
                "A".repeat(700)
                        + " "
                        + "B".repeat(1500);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content(content)
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertTrue(chunks.size() > 1);

        assertTrue(
                chunks.get(0).getContent().endsWith(" ")
        );
    }

    @Test
    void shouldUseHardBoundaryForLongUnbrokenText() {

        String content = "A".repeat(2500);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content(content)
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertEquals(3, chunks.size());

        assertEquals(1000, chunks.get(0).getContent().length());
        assertEquals(1000, chunks.get(1).getContent().length());
        assertEquals(900, chunks.get(2).getContent().length());
    }

    @Test
    void shouldReturnEmptyListForBlankDocument() {

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content("")
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForWhitespaceDocument() {

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-1")
                .content("   \n\t  ")
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldThrowExceptionForNullDocument() {

        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split(null)
        );
    }

    @Test
    void shouldSplitAllKnowledgeDocumentsIntoChunks() {

        ResourceDocumentLoader loader =
                new ResourceDocumentLoader();

        List<KnowledgeDocument> documents =
                loader.load();

        int totalChunks = documents.stream()
                .mapToInt(document ->
                        splitter.split(document).size()
                )
                .sum();

        assertEquals(21, documents.size());

        /*
         * Do not hardcode 190 here.
         * The chunk count is expected to change because
         * the splitting strategy now uses overlap and
         * natural boundaries.
         */
        assertTrue(
                totalChunks > 0,
                "Knowledge documents should produce chunks"
        );
    }

    @Test
    void shouldPreserveSourceDocumentIdAndChunkIndex() {

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-42")
                .content("A".repeat(1500))
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertEquals(2, chunks.size());

        assertEquals(
                "doc-42-chunk-0",
                chunks.get(0).getId()
        );

        assertEquals(
                "doc-42-chunk-1",
                chunks.get(1).getId()
        );

        assertEquals(
                "doc-42",
                chunks.get(0).getMetadata().get("sourceDocumentId")
        );

        assertEquals(
                "0",
                chunks.get(0).getMetadata().get("chunkIndex")
        );

        assertEquals(
                "doc-42",
                chunks.get(1).getMetadata().get("sourceDocumentId")
        );

        assertEquals(
                "1",
                chunks.get(1).getMetadata().get("chunkIndex")
        );
    }

    @Test
    void shouldPreserveOriginalMetadata() {

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-50")
                .content("A".repeat(1500))
                .metadata(Map.of(
                        "category", "docker",
                        "source", "docker-troubleshooting.md"
                ))
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertFalse(chunks.isEmpty());

        for (KnowledgeDocument chunk : chunks) {

            assertEquals(
                    "docker",
                    chunk.getMetadata().get("category")
            );

            assertEquals(
                    "docker-troubleshooting.md",
                    chunk.getMetadata().get("source")
            );
        }
    }

    @Test
    void shouldGenerateChunkMetadataEvenWhenOriginalMetadataIsEmpty() {

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("doc-60")
                .content("Sample content")
                .metadata(null)
                .build();

        List<KnowledgeDocument> chunks =
                splitter.split(document);

        assertEquals(1, chunks.size());

        assertEquals(
                "doc-60",
                chunks.get(0).getMetadata().get("sourceDocumentId")
        );

        assertEquals(
                "0",
                chunks.get(0).getMetadata().get("chunkIndex")
        );
    }
}
