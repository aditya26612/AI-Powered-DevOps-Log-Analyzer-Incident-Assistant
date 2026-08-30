package com.project.llmservice.rag;

import java.util.List;

public interface Retriever {

    List<RetrievalResult> retrieve(
            String query,
            int topK
    );
}