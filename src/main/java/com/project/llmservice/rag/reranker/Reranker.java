package com.project.llmservice.rag.reranker;

import com.project.llmservice.rag.RetrievalResult;

import java.util.List;

public interface Reranker {

    List<RetrievalResult> rerank(
            String query,
            List<RetrievalResult> candidates,
            int topK
    );
}