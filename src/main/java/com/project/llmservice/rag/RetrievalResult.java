package com.project.llmservice.rag;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RetrievalResult {

    private final KnowledgeDocument document;

    private final double score;
}