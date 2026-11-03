
package com.project.llmservice.rag;

import com.project.llmservice.rag.bm25.BM25Index;
import com.project.llmservice.rag.bm25.BM25Retriever;
import com.project.llmservice.rag.splitter.DocumentSplitter;
import com.project.llmservice.vectorstore.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIngestionService {

    private final DocumentLoader documentLoader;
    private final DocumentSplitter documentSplitter;
    private final VectorStoreService vectorStoreService;
    private final BM25Retriever bm25Retriever;

    /*
     * Prevent concurrent ingestion executions
     * within this application instance.
     */
    public synchronized void ingest() {

        log.info("Starting RAG knowledge ingestion");

        // 1. Load source documents.
        List<KnowledgeDocument> documents =
                documentLoader.load();

        if (documents == null || documents.isEmpty()) {
            throw new IllegalStateException(
                    "No knowledge documents were loaded. "
                            + "Aborting synchronization to protect existing data."
            );
        }

        // 2. Validate all source documents before splitting.
        Set<String> managedSourceDocumentIds =
                new LinkedHashSet<>();

        for (KnowledgeDocument document : documents) {

            if (document == null
                    || document.getId() == null
                    || document.getId().isBlank()) {

                throw new IllegalArgumentException(
                        "Loaded document or its ID is invalid"
                );
            }

            if (!managedSourceDocumentIds.add(document.getId())) {

                throw new IllegalArgumentException(
                        "Duplicate source document ID: "
                                + document.getId()
                );
            }
        }

        // 3. Split all validated source documents.
        List<KnowledgeDocument> allChunks =
                new ArrayList<>();

        for (KnowledgeDocument document : documents) {

            List<KnowledgeDocument> chunks =
                    documentSplitter.split(document);

            if (chunks == null || chunks.isEmpty()) {

                throw new IllegalStateException(
                        "Document produced no chunks: "
                                + document.getId()
                );
            }

            allChunks.addAll(chunks);
        }

        log.info(
                "Loaded {} source documents and generated {} chunks",
                managedSourceDocumentIds.size(),
                allChunks.size()
        );

        // 4. Prepare BM25 candidate without publishing it.
        BM25Index.PreparedIndex preparedIndex =
                bm25Retriever.prepareIndex(allChunks);

        // 5. Generate embeddings and synchronize PostgreSQL.
        // If this operation fails, the existing BM25 index remains active.
        vectorStoreService.synchronizeDocuments(
                allChunks,
                managedSourceDocumentIds
        );

        // 6. Publish BM25 only after PostgreSQL succeeds.
        bm25Retriever.publishIndex(preparedIndex);

        log.info(
                "RAG ingestion completed successfully. "
                        + "Sources: {}, Chunks: {}",
                managedSourceDocumentIds.size(),
                allChunks.size()
        );
    }
}
