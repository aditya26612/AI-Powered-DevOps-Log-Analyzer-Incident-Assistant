package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreTest {

    @Test
    void shouldAddAndSearchDocument() {

        VectorStore vectorStore =
                new InMemoryVectorStore();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content(
                                "PostgreSQL connection troubleshooting"
                        )
                        .build();

        float[] embedding = {
                0.1f,
                0.2f,
                0.3f
        };

        vectorStore.add(
                document,
                embedding
        );

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{
                                0.1f,
                                0.2f,
                                0.3f
                        },
                        1
                );

        assertNotNull(results);
        assertEquals(1, results.size());

        assertEquals(
                "doc-1",
                results.get(0)
                        .getDocument()
                        .getId()
        );

        assertEquals(
                1.0,
                results.get(0).getScore(),
                0.0001
        );
    }

    @Test
    void shouldReturnEmptyListWhenTopKIsInvalid() {

        VectorStore vectorStore =
                new InMemoryVectorStore();

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{
                                0.1f,
                                0.2f,
                                0.3f
                        },
                        0
                );

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnMostSimilarDocumentFirst() {

        VectorStore vectorStore =
                new InMemoryVectorStore();

        KnowledgeDocument document1 =
                KnowledgeDocument.builder()
                        .id("doc-1")
                        .content(
                                "Database connection issue"
                        )
                        .build();

        KnowledgeDocument document2 =
                KnowledgeDocument.builder()
                        .id("doc-2")
                        .content(
                                "Kubernetes deployment issue"
                        )
                        .build();

        vectorStore.add(
                document1,
                new float[]{
                        1.0f,
                        0.0f,
                        0.0f
                }
        );

        vectorStore.add(
                document2,
                new float[]{
                        0.0f,
                        1.0f,
                        0.0f
                }
        );

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{
                                0.9f,
                                0.1f,
                                0.0f
                        },
                        2
                );

        assertEquals(2, results.size());

        assertEquals(
                "doc-1",
                results.get(0)
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

        VectorStore vectorStore =
                new InMemoryVectorStore();

        for (int i = 1; i <= 3; i++) {

            KnowledgeDocument document =
                    KnowledgeDocument.builder()
                            .id("doc-" + i)
                            .content(
                                    "Document " + i
                            )
                            .build();

            vectorStore.add(
                    document,
                    new float[]{
                            (float) i,
                            0.0f,
                            0.0f
                    }
            );
        }

        List<RetrievalResult> results =
                vectorStore.search(
                        new float[]{
                                1.0f,
                                0.0f,
                                0.0f
                        },
                        2
                );

        assertEquals(2, results.size());
    }
}