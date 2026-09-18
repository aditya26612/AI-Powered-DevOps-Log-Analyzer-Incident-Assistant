package com.project.llmservice.rag;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.vectorstore.InMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorRetrieverIntegrationTest {

    private EmbeddingService embeddingService;

    private InMemoryVectorStore vectorStore;

    private VectorRetriever vectorRetriever;

    @BeforeEach
    void setUp() {

        embeddingService = org.mockito.Mockito.mock(
                EmbeddingService.class
        );

        vectorStore = new InMemoryVectorStore();

        vectorRetriever = new VectorRetriever(
                embeddingService,
                vectorStore
        );
    }

    @Test
    void shouldRetrieveMostSimilarDocument() {

        KnowledgeDocument databaseDocument =
                KnowledgeDocument.builder()
                        .id("database-doc")
                        .content(
                                "PostgreSQL database connection troubleshooting"
                        )
                        .build();

        KnowledgeDocument kubernetesDocument =
                KnowledgeDocument.builder()
                        .id("kubernetes-doc")
                        .content(
                                "Kubernetes deployment troubleshooting"
                        )
                        .build();

        vectorStore.add(
                databaseDocument,
                new float[]{
                        1.0f,
                        0.0f,
                        0.0f
                }
        );

        vectorStore.add(
                kubernetesDocument,
                new float[]{
                        0.0f,
                        1.0f,
                        0.0f
                }
        );

        String query =
                "PostgreSQL connection failed";

        whenEmbedding(
                query,
                new float[]{
                        0.9f,
                        0.1f,
                        0.0f
                }
        );

        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        query,
                        2
                );

        assertNotNull(results);

        assertEquals(
                2,
                results.size()
        );

        assertEquals(
                "database-doc",
                results.get(0)
                        .getDocument()
                        .getId()
        );

        assertEquals(
                "kubernetes-doc",
                results.get(1)
                        .getDocument()
                        .getId()
        );

        assertTrue(
                results.get(0).getScore()
                        > results.get(1).getScore()
        );
    }

    @Test
    void shouldRespectTopK() {

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

        KnowledgeDocument document3 =
                KnowledgeDocument.builder()
                        .id("doc-3")
                        .content("Docker troubleshooting")
                        .build();

        vectorStore.add(
                document1,
                new float[]{1.0f, 0.0f, 0.0f}
        );

        vectorStore.add(
                document2,
                new float[]{0.8f, 0.2f, 0.0f}
        );

        vectorStore.add(
                document3,
                new float[]{0.0f, 1.0f, 0.0f}
        );

        String query =
                "database problem";

        whenEmbedding(
                query,
                new float[]{1.0f, 0.0f, 0.0f}
        );

        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        query,
                        2
                );

        assertNotNull(results);

        assertEquals(
                2,
                results.size()
        );

        assertEquals(
                "doc-1",
                results.get(0)
                        .getDocument()
                        .getId()
        );

        assertEquals(
                "doc-2",
                results.get(1)
                        .getDocument()
                        .getId()
        );
    }

    @Test
    void shouldReturnEmptyListForInvalidQuery() {

        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        "",
                        3
                );

        assertNotNull(results);

        assertTrue(
                results.isEmpty()
        );

        org.mockito.Mockito.verifyNoInteractions(
                embeddingService
        );
    }

    @Test
    void shouldReturnEmptyListForInvalidTopK() {

        List<RetrievalResult> results =
                vectorRetriever.retrieve(
                        "database error",
                        0
                );

        assertNotNull(results);

        assertTrue(
                results.isEmpty()
        );

        org.mockito.Mockito.verifyNoInteractions(
                embeddingService
        );
    }

    private void whenEmbedding(
            String query,
            float[] embedding
    ) {

        org.mockito.Mockito.when(
                embeddingService.embed(query)
        ).thenReturn(embedding);
    }
}