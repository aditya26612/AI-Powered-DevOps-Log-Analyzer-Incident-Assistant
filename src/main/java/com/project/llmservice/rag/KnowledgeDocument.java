package com.project.llmservice.rag;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class KnowledgeDocument {

    private final String id;

    private final String content;

    private final Map<String, String> metadata;
}