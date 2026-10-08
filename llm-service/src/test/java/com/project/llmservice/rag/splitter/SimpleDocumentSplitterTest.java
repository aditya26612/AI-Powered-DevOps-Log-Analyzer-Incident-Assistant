package com.project.llmservice.rag.splitter;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.ResourceDocumentLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleDocumentSplitterTest {

    private SimpleDocumentSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new SimpleDocumentSplitter();
    }

    @Test
    void shouldSplitDocumentIntoChunks() {

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
        assertEquals(500, chunks.get(2).getContent().length());
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
    void shouldThrowExceptionForNullDocument() {

        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split(null)
        );
    }

    @Test
    void shouldSplitAllKnowledgeDocumentsIntoExpectedChunks() {

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
        assertEquals(189, totalChunks);
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
    }
}