package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.Retriever;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BM25Retriever implements Retriever {

    private final BM25Index bm25Index;
    private final DocumentSplitter documentSplitter;

    private boolean initialized = false;

    public void indexDocuments(
            List<KnowledgeDocument> documents
    ) {

        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (KnowledgeDocument document : documents) {

            List<KnowledgeDocument> chunks =
                    documentSplitter.split(document);

            for (KnowledgeDocument chunk : chunks) {
                bm25Index.addDocument(chunk);
            }
        }

        initialized = true;
    }

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {

        if (!initialized) {
            return List.of();
        }

        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        return bm25Index.search(query, topK);
    }
}