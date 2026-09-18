package com.project.llmservice.rag;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import com.project.llmservice.rag.splitter.SimpleDocumentSplitter;
import com.project.llmservice.vectorstore.InMemoryVectorStore;
import com.project.llmservice.vectorstore.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagIngestionRetrievalIntegrationTest {

    private DocumentLoader documentLoader;

    private DocumentSplitter documentSplitter;

    private EmbeddingService embeddingService;

    private InMemoryVectorStore vectorStore;

    private RagIngestionService ragIngestionService;

    private VectorRetriever vectorRetriever;

    @BeforeEach
    void setUp() {

        documentLoader = mock(DocumentLoader.class);

        documentSplitter = new SimpleDocumentSplitter();

        embeddingService = mock(EmbeddingService.class);

        vectorStore = new InMemoryVectorStore();

        VectorStoreService vectorStoreService =
                new VectorStoreService(
                        embeddingService,
                        vectorStore
                );

        ragIngestionService =
                new RagIngestionService(
                        documentLoader,
                        documentSplitter,
                        vectorStoreService
                );

        vectorRetriever =
                new VectorRetriever(
                        embeddingService,
                        vectorStore
                );
    }

    @Test
    void shouldIngestDocumentAndRetrieveIt() {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("database-doc")
                        .content(
                                "PostgreSQL database connection troubleshooting"
                        )
                        .build();

        when(documentLoader.load())
                .thenReturn(List.of(document));

        when(embeddingService.embed(anyString()))
                .thenAnswer(invocation -> {

                    String text =
                            invocation.getArgument(0);

                    if (text.contains("PostgreSQL")) {
                        return new float[]{
                                1.0f,
                                0.0f,
                                0.0f
                        };
                    }

                    return new float[]{
                            0.9f,
                            0.1f,
                            0.0f
                    };
                });

        // Ingest document
        ragIngestionService.ingest();

        // Retrieve using a similar query
        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        "PostgreSQL connection failed",
                        1
                );

        assertNotNull(results);

        assertEquals(
                1,
                results.size()
        );

        assertEquals(
                "database-doc-chunk-0",
                results.get(0)
                        .getDocument()
                        .getId()
        );

        assertTrue(
                results.get(0).getScore() > 0
        );

        verify(documentLoader)
                .load();
    }
}