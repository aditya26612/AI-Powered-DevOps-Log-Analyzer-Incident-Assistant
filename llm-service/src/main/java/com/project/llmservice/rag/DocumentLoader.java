package com.project.llmservice.rag;

import java.util.List;

public interface DocumentLoader {

    List<KnowledgeDocument> load();
}