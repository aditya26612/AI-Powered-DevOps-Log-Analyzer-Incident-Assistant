package com.project.llmservice.rag.impl;

import com.project.llmservice.rag.DocumentLoader;
import com.project.llmservice.rag.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class FileSystemDocumentLoader implements DocumentLoader {

    private static final String KNOWLEDGE_PATH = "knowledge/";

    @Override
    public List<KnowledgeDocument> load() {

        List<KnowledgeDocument> documents = new ArrayList<>();

        try {
            loadDocument(
                    "kubernetes.md",
                    documents
            );

            loadDocument(
                    "docker.md",
                    documents
            );

            loadDocument(
                    "spring-boot.md",
                    documents
            );

            loadDocument(
                    "database.md",
                    documents
            );

            return documents;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to load knowledge documents",
                    e
            );
        }
    }

    private void loadDocument(
            String fileName,
            List<KnowledgeDocument> documents
    ) throws IOException {

        String path = KNOWLEDGE_PATH + fileName;

        try (InputStream inputStream =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(path)) {

            if (inputStream == null) {
                throw new IOException(
                        "Knowledge document not found: " + path
                );
            }

            String content =
                    new String(
                            inputStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            documents.add(
                    KnowledgeDocument.builder()
                            .id(fileName)
                            .content(content)
                            .metadata(null)
                            .build()
            );
        }
    }
}