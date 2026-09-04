package com.project.llmservice.rag.splitter;

import com.project.llmservice.rag.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SimpleDocumentSplitter implements DocumentSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 1000;

    @Override
    public List<KnowledgeDocument> split(KnowledgeDocument document) {

        if (document == null) {
            throw new IllegalArgumentException("Document must not be null");
        }

        String content = document.getContent();

        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<KnowledgeDocument> chunks = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;

        while (start < content.length()) {

            int end = Math.min(
                    start + DEFAULT_CHUNK_SIZE,
                    content.length()
            );

            String chunkContent = content.substring(start, end);

            chunks.add(
                    KnowledgeDocument.builder()
                            .id(document.getId() + "-chunk-" + chunkIndex)
                            .content(chunkContent)
                            .metadata(Map.of(
                                    "sourceDocumentId", document.getId(),
                                    "chunkIndex", String.valueOf(chunkIndex)
                            ))
                            .build()
            );

            start = end;
            chunkIndex++;
        }

        return chunks;
    }
}