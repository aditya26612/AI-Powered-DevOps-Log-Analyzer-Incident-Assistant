package com.project.llmservice.rag.reranker;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LexicalRerankerTest {

    private final LexicalReranker reranker =
            new LexicalReranker();

    @Test
    void shouldRankMoreRelevantDocumentHigher() {

        RetrievalResult weakMatch =
                result(
                        "Docker image cannot be pulled",
                        "Docker image pull failed"
                );

        RetrievalResult strongMatch =
                result(
                        "Docker container keeps restarting",
                        "Docker container keeps restarting because the application crashes"
                );

        List<RetrievalResult> results =
                reranker.rerank(
                        "Docker container keeps restarting",
                        List.of(weakMatch, strongMatch),
                        2
                );

        assertEquals(
                strongMatch.getDocument(),
                results.get(0).getDocument()
        );

        assertTrue(
                results.get(0).getScore()
                        > results.get(1).getScore()
        );
    }

    @Test
    void shouldRespectTopK() {

        List<RetrievalResult> candidates =
                List.of(
                        result("Docker error", "Docker container error"),
                        result("Kubernetes error", "Kubernetes pod error"),
                        result("Nginx error", "Nginx upstream error")
                );

        List<RetrievalResult> results =
                reranker.rerank(
                        "Docker error",
                        candidates,
                        2
                );

        assertEquals(2, results.size());
    }

    @Test
    void shouldReturnEmptyForInvalidInput() {

        List<RetrievalResult> results =
                reranker.rerank(
                        "",
                        List.of(),
                        3
                );

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnEmptyForInvalidTopK() {

        RetrievalResult candidate =
                result(
                        "Docker error",
                        "Docker container error"
                );

        List<RetrievalResult> results =
                reranker.rerank(
                        "Docker error",
                        List.of(candidate),
                        0
                );

        assertTrue(results.isEmpty());
    }

    private RetrievalResult result(
            String id,
            String content
    ) {

        KnowledgeDocument document =
                KnowledgeDocument.builder()
                        .id(id)
                        .content(content)
                        .metadata(Map.of())
                        .build();

        return RetrievalResult.builder()
                .document(document)
                .score(0.5)
                .build();
    }
}