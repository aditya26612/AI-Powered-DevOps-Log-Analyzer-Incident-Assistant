
package com.project.llmservice.rag;

import com.project.llmservice.rag.bm25.BM25Index;
import com.project.llmservice.rag.bm25.BM25Retriever;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import com.project.llmservice.vectorstore.VectorStoreService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagIngestionServiceTest {

    @Mock
    private DocumentLoader documentLoader;

    @Mock
    private DocumentSplitter documentSplitter;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private BM25Retriever bm25Retriever;

    private RagIngestionService ragIngestionService;

    @BeforeEach
    void setUp() {

        ragIngestionService =
                new RagIngestionService(
                        documentLoader,
                        documentSplitter,
                        vectorStoreService,
                        bm25Retriever
                );
    }

    @Test
    void shouldLoadPrepareSynchronizeAndPublishIndex() {

        KnowledgeDocument document =
                createSourceDocument("doc-1");

        KnowledgeDocument chunk1 =
                createChunk("doc-1-chunk-0", "doc-1");

        KnowledgeDocument chunk2 =
                createChunk("doc-1-chunk-1", "doc-1");

        List<KnowledgeDocument> chunks =
                List.of(chunk1, chunk2);

        BM25Index.PreparedIndex preparedIndex =
                mock(BM25Index.PreparedIndex.class);

        when(documentLoader.load())
                .thenReturn(List.of(document));

        when(documentSplitter.split(document))
                .thenReturn(chunks);

        when(bm25Retriever.prepareIndex(chunks))
                .thenReturn(preparedIndex);

        ragIngestionService.ingest();

        verify(documentLoader).load();
        verify(documentSplitter).split(document);

        InOrder order =
                inOrder(bm25Retriever, vectorStoreService);

        order.verify(bm25Retriever)
                .prepareIndex(chunks);

        order.verify(vectorStoreService)
                .synchronizeDocuments(
                        chunks,
                        Set.of("doc-1")
                );

        order.verify(bm25Retriever)
                .publishIndex(preparedIndex);

        verify(bm25Retriever, never())
                .rebuildIndex(anyList());

        verify(vectorStoreService, never())
                .addDocument(any());
    }

    @Test
    void shouldRejectEmptyDocumentList() {

        when(documentLoader.load())
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> ragIngestionService.ingest()
        );

        verify(documentLoader).load();

        verifyNoInteractions(documentSplitter);
        verifyNoInteractions(vectorStoreService);
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    void shouldProcessMultipleDocuments() {

        KnowledgeDocument document1 =
                createSourceDocument("doc-1");

        KnowledgeDocument document2 =
                createSourceDocument("doc-2");

        KnowledgeDocument chunk1 =
                createChunk("doc-1-chunk-0", "doc-1");

        KnowledgeDocument chunk2 =
                createChunk("doc-2-chunk-0", "doc-2");

        List<KnowledgeDocument> chunks =
                List.of(chunk1, chunk2);

        BM25Index.PreparedIndex preparedIndex =
                mock(BM25Index.PreparedIndex.class);

        when(documentLoader.load())
                .thenReturn(List.of(document1, document2));

        when(documentSplitter.split(document1))
                .thenReturn(List.of(chunk1));

        when(documentSplitter.split(document2))
                .thenReturn(List.of(chunk2));

        when(bm25Retriever.prepareIndex(chunks))
                .thenReturn(preparedIndex);

        ragIngestionService.ingest();

        verify(documentSplitter).split(document1);
        verify(documentSplitter).split(document2);

        verify(vectorStoreService).synchronizeDocuments(
                chunks,
                Set.of("doc-1", "doc-2")
        );

        verify(bm25Retriever).publishIndex(preparedIndex);
    }

    @Test
    void shouldNotPublishBm25WhenPostgresSynchronizationFails() {

        KnowledgeDocument document =
                createSourceDocument("doc-1");

        KnowledgeDocument chunk =
                createChunk("doc-1-chunk-0", "doc-1");

        List<KnowledgeDocument> chunks =
                List.of(chunk);

        BM25Index.PreparedIndex preparedIndex =
                mock(BM25Index.PreparedIndex.class);

        when(documentLoader.load())
                .thenReturn(List.of(document));

        when(documentSplitter.split(document))
                .thenReturn(chunks);

        when(bm25Retriever.prepareIndex(chunks))
                .thenReturn(preparedIndex);

        doThrow(
                new RuntimeException(
                        "Database synchronization failed"
                )
        ).when(vectorStoreService)
                .synchronizeDocuments(
                        chunks,
                        Set.of("doc-1")
                );

        assertThrows(
                RuntimeException.class,
                () -> ragIngestionService.ingest()
        );

        verify(bm25Retriever).prepareIndex(chunks);

        verify(vectorStoreService).synchronizeDocuments(
                chunks,
                Set.of("doc-1")
        );

        verify(bm25Retriever, never())
                .publishIndex(any());
    }

    @Test
    void shouldRejectDuplicateSourceDocumentIds() {

        KnowledgeDocument document1 =
                createSourceDocument("doc-1");

        KnowledgeDocument document2 =
                createSourceDocument("doc-1");

        when(documentLoader.load())
                .thenReturn(List.of(document1, document2));

        assertThrows(
                IllegalArgumentException.class,
                () -> ragIngestionService.ingest()
        );

        verifyNoInteractions(documentSplitter);
        verifyNoInteractions(vectorStoreService);
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    void shouldRejectDocumentWithoutChunks() {

        KnowledgeDocument document =
                createSourceDocument("doc-1");

        when(documentLoader.load())
                .thenReturn(List.of(document));

        when(documentSplitter.split(document))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> ragIngestionService.ingest()
        );

        verifyNoInteractions(vectorStoreService);
        verifyNoInteractions(bm25Retriever);
    }

    private KnowledgeDocument createSourceDocument(String id) {

        return KnowledgeDocument.builder()
                .id(id)
                .content("Knowledge document content")
                .build();
    }

    private KnowledgeDocument createChunk(
            String id,
            String sourceDocumentId
    ) {

        return KnowledgeDocument.builder()
                .id(id)
                .content("Knowledge chunk content")
                .metadata(
                        Map.of(
                                "sourceDocumentId",
                                sourceDocumentId
                        )
                )
                .build();
    }
}
