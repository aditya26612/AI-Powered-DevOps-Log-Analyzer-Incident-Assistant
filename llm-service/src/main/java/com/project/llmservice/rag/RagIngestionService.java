package com.project.llmservice.rag;

import com.project.llmservice.rag.splitter.DocumentSplitter;
import com.project.llmservice.vectorstore.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagIngestionService {

    private final DocumentLoader documentLoader;
    private final DocumentSplitter documentSplitter;
    private final VectorStoreService vectorStoreService;

    public void ingest() {

        List<KnowledgeDocument> documents =
                documentLoader.load();

        for (KnowledgeDocument document : documents) {

            List<KnowledgeDocument> chunks =
                    documentSplitter.split(document);

            for (KnowledgeDocument chunk : chunks) {
                vectorStoreService.addDocument(chunk);
            }
        }
    }
}