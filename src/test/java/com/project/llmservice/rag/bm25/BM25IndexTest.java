package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BM25IndexTest {

    @Test
    void shouldRankMostRelevantDocumentFirst() {

        BM25Index index = new BM25Index();

        KnowledgeDocument databaseConnection =
                KnowledgeDocument.builder()
                        .id("database-connection")
                        .content("database connection failed")
                        .build();

        KnowledgeDocument nginxTimeout =
                KnowledgeDocument.builder()
                        .id("nginx-timeout")
                        .content("nginx upstream timeout")
                        .build();

        KnowledgeDocument databaseAuthentication =
                KnowledgeDocument.builder()
                        .id("database-authentication")
                        .content("database authentication failed")
                        .build();

        index.addDocument(databaseConnection);
        index.addDocument(nginxTimeout);
        index.addDocument(databaseAuthentication);

        List<RetrievalResult> results =
                index.search(
                        "database connection failed",
                        3
                );

        assertEquals(3, results.size());

        assertEquals(
                "database-connection",
                results.get(0).getDocument().getId()
        );

        assertTrue(
                results.get(0).getScore()
                        > results.get(1).getScore()
        );
    }

    @Test
    void shouldRespectTopK() {

        BM25Index index = new BM25Index();

        for (int i = 0; i < 5; i++) {

            index.addDocument(
                    KnowledgeDocument.builder()
                            .id("document-" + i)
                            .content(
                                    "database connection failed"
                            )
                            .build()
            );
        }

        List<RetrievalResult> results =
                index.search(
                        "database connection",
                        2
                );

        assertEquals(2, results.size());
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {

        BM25Index index = new BM25Index();

        index.addDocument(
                KnowledgeDocument.builder()
                        .id("test-document")
                        .content("database connection failed")
                        .build()
        );

        assertTrue(
                index.search("", 5).isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyForInvalidTopK() {

        BM25Index index = new BM25Index();

        index.addDocument(
                KnowledgeDocument.builder()
                        .id("test-document")
                        .content("database connection failed")
                        .build()
        );

        assertTrue(
                index.search(
                        "database connection",
                        0
                ).isEmpty()
        );
    }

    @Test
    void shouldRejectNullDocument() {

        BM25Index index = new BM25Index();

        assertThrows(
                IllegalArgumentException.class,
                () -> index.addDocument(null)
        );
    }

    @Test
    void shouldRejectBlankDocumentContent() {

        BM25Index index = new BM25Index();

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id("blank-document")
                        .content("   ")
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> index.addDocument(document)
        );
    }
}