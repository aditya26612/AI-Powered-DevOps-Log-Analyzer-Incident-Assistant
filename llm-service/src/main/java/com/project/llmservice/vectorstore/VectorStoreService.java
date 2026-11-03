
package com.project.llmservice.vectorstore;

import com.project.llmservice.embeddings.EmbeddingService;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    /*
     * Existing single-document operation.
     * Retained for backward compatibility.
     */
    public void addDocument(KnowledgeDocument document) {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (document.getContent() == null
                || document.getContent().isBlank()) {

            throw new IllegalArgumentException(
                    "Document content must not be null or blank"
            );
        }

        float[] embedding =
                embeddingService.embed(
                        document.getContent()
                );

        vectorStore.add(
                document,
                embedding
        );
    }

    /*
     * Bulk synchronization operation.
     *
     * All embeddings are generated before the vector store
     * is modified.
     */
    public void synchronizeDocuments(
            List<KnowledgeDocument> chunks,
            Set<String> managedSourceDocumentIds
    ) {

        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chunks must not be null or empty"
            );
        }

        if (managedSourceDocumentIds == null
                || managedSourceDocumentIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Managed source document IDs must not be empty"
            );
        }

        Set<String> sourceIds = new HashSet<>();

        for (String sourceId : managedSourceDocumentIds) {

            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException(
                        "Managed source document ID must not be blank"
                );
            }

            sourceIds.add(sourceId);
        }

        Set<String> chunkIds = new HashSet<>();
        Set<String> representedSourceIds = new HashSet<>();

        /*
         * Phase 1: Validate the complete batch.
         * No embeddings or store modifications yet.
         */
        for (KnowledgeDocument chunk : chunks) {

            if (chunk == null) {
                throw new IllegalArgumentException(
                        "Chunk must not be null"
                );
            }

            if (chunk.getId() == null || chunk.getId().isBlank()) {
                throw new IllegalArgumentException(
                        "Chunk ID must not be blank"
                );
            }

            if (chunk.getContent() == null
                    || chunk.getContent().isBlank()) {
                throw new IllegalArgumentException(
                        "Chunk content must not be null or blank"
                );
            }

            if (!chunkIds.add(chunk.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate chunk ID: " + chunk.getId()
                );
            }

            Map<String, String> metadata = chunk.getMetadata();

            String sourceDocumentId =
                    metadata == null
                            ? null
                            : metadata.get("sourceDocumentId");

            if (sourceDocumentId == null
                    || !sourceIds.contains(sourceDocumentId)) {
                throw new IllegalArgumentException(
                        "Chunk has missing or unmanaged sourceDocumentId: "
                                + chunk.getId()
                );
            }

            representedSourceIds.add(sourceDocumentId);
        }

        if (!representedSourceIds.equals(sourceIds)) {
            throw new IllegalArgumentException(
                    "Every managed source document must have at least one chunk"
            );
        }

        /*
         * Phase 2: Generate all embeddings.
         *
         * The vector store is not touched during this phase.
         */
        List<EmbeddedDocument> embeddedDocuments =
                new ArrayList<>(chunks.size());

        for (KnowledgeDocument chunk : chunks) {

            float[] embedding =
                    embeddingService.embed(
                            chunk.getContent()
                    );

            embeddedDocuments.add(
                    new EmbeddedDocument(
                            chunk,
                            embedding
                    )
            );
        }

        /*
         * Phase 3: Synchronize only after every embedding
         * has been generated successfully.
         */
        vectorStore.synchronize(
                embeddedDocuments,
                sourceIds
        );
    }

    /*
     * Existing retrieval operation.
     */
    public List<RetrievalResult> search(
            String query,
            int topK
    ) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (topK <= 0) {
            return List.of();
        }

        float[] queryEmbedding =
                embeddingService.embed(query);

        return vectorStore.search(
                queryEmbedding,
                topK
        );
    }
}
