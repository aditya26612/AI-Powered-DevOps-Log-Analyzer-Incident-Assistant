package com.project.llmservice.rag.splitter;

import com.project.llmservice.rag.KnowledgeDocument;

import java.util.List;

public interface DocumentSplitter {

    List<KnowledgeDocument> split(KnowledgeDocument document);
}