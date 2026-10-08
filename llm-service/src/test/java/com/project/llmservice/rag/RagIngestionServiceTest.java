package com.project.llmservice.rag;

import com.project.llmservice.rag.splitter.DocumentSplitter;
import com.project.llmservice.vectorstore.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagIngestionServiceTest {

    @Mock
    private DocumentLoader documentLoader;

    @Mock
    private DocumentSplitter documentSplitter;

    @Mock
    private VectorStoreService vectorStoreService;

    private RagIngestionService ragIngestionService;

    @BeforeEach
    void setUp() {

        ragIngestionService =
                new RagIngestionService(
                        documentLoader,
                        documentSplitter,
                        vectorStoreService
                );
    }

    @Test
    void shouldLoadSplitAndStoreDocuments() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content(
                                "PostgreSQL connection troubleshooting"
                        )
                        .build();

        KnowledgeDocument chunk1 =
                KnowledgeDocument.builder()
                        .id("doc-1-chunk-0")
                        .content("PostgreSQL connection")
                        .build();

        KnowledgeDocument chunk2 =
                KnowledgeDocument.builder()
                        .id("doc-1-chunk-1")
                        .content("troubleshooting")
                        .build();

        when(documentLoader.load())
                .thenReturn(List.of(document));

        when(documentSplitter.split(document))
                .thenReturn(List.of(chunk1, chunk2));

        ragIngestionService.ingest();

        verify(documentLoader)
                .load();

        verify(documentSplitter)
                .split(document);

        verify(vectorStoreService)
                .addDocument(chunk1);

        verify(vectorStoreService)
                .addDocument(chunk2);
    }

    @Test
    void shouldHandleNoDocuments() {

        when(documentLoader.load())
                .thenReturn(List.of());

        ragIngestionService.ingest();

        verify(documentLoader)
                .load();

        verifyNoInteractions(documentSplitter);
        verifyNoInteractions(vectorStoreService);
    }

    @Test
    void shouldProcessMultipleDocuments() {

        KnowledgeDocument document1 =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content("Database troubleshooting")
                        .build();

        KnowledgeDocument document2 =
                KnowledgeDocument.builder()
                        .id("doc-2")
                        .content("Kubernetes troubleshooting")
                        .build();

        when(documentLoader.load())
                .thenReturn(List.of(
                        document1,
                        document2
                ));

        when(documentSplitter.split(document1))
                .thenReturn(List.of(document1));

        when(documentSplitter.split(document2))
                .thenReturn(List.of(document2));

        ragIngestionService.ingest();

        verify(documentSplitter)
                .split(document1);

        verify(documentSplitter)
                .split(document2);

        verify(vectorStoreService)
                .addDocument(document1);

        verify(vectorStoreService)
                .addDocument(document2);
    }
}