
package com.project.llmservice.integration;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.properties.RagProperties;
import com.project.llmservice.rag.*;
import com.project.llmservice.rag.bm25.BM25Index;
import com.project.llmservice.rag.bm25.BM25Retriever;
import com.project.llmservice.rag.splitter.SimpleDocumentSplitter;
import com.project.llmservice.vectorstore.InMemoryVectorStore;
import com.project.llmservice.vectorstore.VectorStoreService;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIngestionRetrievalIntegrationTest {

    @Test
    void shouldIngestAndRetrieveThroughDenseAndBm25Retrievers() {

        // 1. Prepare source document
        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("database-doc")
                        .content(
                                "PostgreSQL database connection troubleshooting"
                        )
                        .build();

        // 2. Mock document loader
        DocumentLoader documentLoader =
                mock(DocumentLoader.class);

        when(documentLoader.load())
                .thenReturn(List.of(document));

        // 3. Real document splitter
        SimpleDocumentSplitter documentSplitter =
                new SimpleDocumentSplitter();

        // 4. Mock embedding service
        EmbeddingService embeddingService =
                mock(EmbeddingService.class);

        when(embeddingService.embed(
                "PostgreSQL database connection troubleshooting"
        )).thenReturn(new float[]{
                1.0f, 0.0f, 0.0f
        });

        when(embeddingService.embed(
                "database connection"
        )).thenReturn(new float[]{
                1.0f, 0.0f, 0.0f
        });

        // 5. Real in-memory vector store
        InMemoryVectorStore inMemoryVectorStore =
                new InMemoryVectorStore();

        // Spy preserves real synchronization behavior.
        VectorStoreService vectorStoreService =
                spy(new VectorStoreService(
                        embeddingService,
                        inMemoryVectorStore
                ));

        // 6. Real BM25 index and retriever
        BM25Index bm25Index = new BM25Index();

        BM25Retriever bm25Retriever =
                spy(new BM25Retriever(bm25Index));

        // 7. Create ingestion service
        RagIngestionService ingestionService =
                new RagIngestionService(
                        documentLoader,
                        documentSplitter,
                        vectorStoreService,
                        bm25Retriever
                );

        // 8. Execute ingestion
        ingestionService.ingest();

        // 9. Verify ingestion operation ordering
        InOrder order = inOrder(
                bm25Retriever,
                vectorStoreService
        );

        order.verify(bm25Retriever)
                .prepareIndex(anyList());

        order.verify(vectorStoreService)
                .synchronizeDocuments(anyList(), anySet());

        order.verify(bm25Retriever)
                .publishIndex(any(BM25Index.PreparedIndex.class));

        // 10. Configure dense retrieval
        RagProperties ragProperties =
                new RagProperties();

        ragProperties.setSimilarityThreshold(0.70);

        VectorRetriever vectorRetriever =
                new VectorRetriever(
                        vectorStoreService,
                        ragProperties
                );

        // 11. Test dense retrieval
        List<RetrievalResult> denseResults =
                vectorRetriever.retrieve(
                        "database connection",
                        1
                );

        assertFalse(denseResults.isEmpty());

        assertEquals(
                "database-doc-chunk-0",
                denseResults.get(0)
                        .getDocument()
                        .getId()
        );

        // 12. Test actual BM25 retrieval
        List<RetrievalResult> bm25Results =
                bm25Retriever.retrieve(
                        "database connection",
                        1
                );

        assertFalse(bm25Results.isEmpty());

        assertEquals(
                "database-doc-chunk-0",
                bm25Results.get(0)
                        .getDocument()
                        .getId()
        );
    }
}
