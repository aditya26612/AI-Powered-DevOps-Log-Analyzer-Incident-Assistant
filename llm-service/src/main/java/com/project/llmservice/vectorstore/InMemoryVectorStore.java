
package com.project.llmservice.vectorstore;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
        name = "rag.vector-store",
        havingValue = "memory"
)
@Component
public class InMemoryVectorStore implements VectorStore {

    /*
     * Each update publishes a new immutable snapshot.
     * Searches always read a consistent snapshot.
     */
    private volatile List<VectorEntry> entries = List.of();

    // ---------------------------------------------------------
    // ADD
    // ---------------------------------------------------------

    @Override
    public synchronized void add(
            KnowledgeDocument document,
            float[] embedding
    ) {

        validate(document, embedding);

        List<VectorEntry> currentSnapshot = entries;

        /*
         * Enforce the same embedding dimension across
         * the complete vector store.
         */
        Integer existingDimension =
                getExistingEmbeddingDimension(currentSnapshot);

        validateEmbeddingDimension(
                embedding,
                existingDimension
        );

        List<VectorEntry> updated =
                new ArrayList<>(currentSnapshot);

        // Replace an existing chunk with the same ID.
        updated.removeIf(
                entry -> entry.document().getId()
                        .equals(document.getId())
        );

        updated.add(new VectorEntry(document, embedding));

        // Publish the complete updated snapshot.
        entries = List.copyOf(updated);
    }

    // ---------------------------------------------------------
    // SYNCHRONIZE
    // ---------------------------------------------------------

    @Override
    public synchronized void synchronize(
            List<EmbeddedDocument> documents,
            Set<String> managedSourceDocumentIds
    ) {

        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Documents must not be null or empty"
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

        Set<String> currentChunkIds = new HashSet<>();
        Set<String> representedSourceIds = new HashSet<>();

        /*
         * Phase 1: Validate the complete incoming batch.
         *
         * No existing entries are modified here.
         */

        Integer batchEmbeddingDimension = null;

        for (EmbeddedDocument embeddedDocument : documents) {

            if (embeddedDocument == null) {
                throw new IllegalArgumentException(
                        "Embedded document must not be null"
                );
            }

            KnowledgeDocument document =
                    embeddedDocument.document();

            float[] embedding =
                    embeddedDocument.embedding();

            validate(document, embedding);

            // Check duplicate chunk IDs.
            if (!currentChunkIds.add(document.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate chunk ID: " + document.getId()
                );
            }

            /*
             * All embeddings in this incoming batch
             * must have identical dimensions.
             */
            if (batchEmbeddingDimension == null) {

                batchEmbeddingDimension = embedding.length;

            } else {

                validateEmbeddingDimension(
                        embedding,
                        batchEmbeddingDimension
                );
            }

            // Validate source metadata.
            Map<String, String> metadata =
                    document.getMetadata();

            String sourceDocumentId =
                    metadata == null
                            ? null
                            : metadata.get("sourceDocumentId");

            if (sourceDocumentId == null
                    || !sourceIds.contains(sourceDocumentId)) {

                throw new IllegalArgumentException(
                        "Chunk has missing or unmanaged sourceDocumentId: "
                                + document.getId()
                );
            }

            representedSourceIds.add(sourceDocumentId);
        }

        /*
         * Every managed source must have at least
         * one incoming chunk.
         */
        if (!representedSourceIds.equals(sourceIds)) {
            throw new IllegalArgumentException(
                    "Every managed source document must have at least one chunk"
            );
        }

        /*
         * Phase 2: Check dimensions against existing data.
         *
         * We don't allow a new batch to introduce vectors
         * with dimensions different from the current index.
         */
        List<VectorEntry> currentSnapshot = entries;

        Integer existingDimension =
                getExistingEmbeddingDimension(currentSnapshot);

        validateEmbeddingDimension(
                new float[batchEmbeddingDimension],
                existingDimension
        );

        /*
         * Phase 3: Build a new collection.
         *
         * Preserve unmanaged documents.
         * Remove old chunks belonging to managed sources.
         * Also remove matching IDs to prevent duplicates.
         */
        List<VectorEntry> updated = new ArrayList<>();

        for (VectorEntry existing : currentSnapshot) {

            Map<String, String> metadata =
                    existing.document().getMetadata();

            String sourceDocumentId =
                    metadata == null
                            ? null
                            : metadata.get("sourceDocumentId");

            boolean belongsToManagedSource =
                    sourceDocumentId != null
                            && sourceIds.contains(sourceDocumentId);

            boolean idWillBeReplaced =
                    currentChunkIds.contains(
                            existing.document().getId()
                    );

            if (!belongsToManagedSource && !idWillBeReplaced) {
                updated.add(existing);
            }
        }

        /*
         * Phase 4: Add all current chunks.
         */
        for (EmbeddedDocument embeddedDocument : documents) {

            updated.add(
                    new VectorEntry(
                            embeddedDocument.document(),
                            embeddedDocument.embedding()
                    )
            );
        }

        /*
         * Phase 5: Publish everything at once.
         */
        entries = List.copyOf(updated);
    }

    // ---------------------------------------------------------
    // SEARCH
    // ---------------------------------------------------------

    @Override
    public List<RetrievalResult> search(
            float[] queryEmbedding,
            int topK
    ) {

        if (queryEmbedding == null
                || queryEmbedding.length == 0
                || topK <= 0) {

            return List.of();
        }

        validateQueryEmbedding(queryEmbedding);

        /*
         * Read one consistent snapshot for this search.
         */
        List<VectorEntry> snapshot = entries;

        if (snapshot.isEmpty()) {
            return List.of();
        }

        /*
         * Query dimensions must match the dimensions
         * used by the stored document embeddings.
         */
        Integer storedDimension =
                getExistingEmbeddingDimension(snapshot);

        validateEmbeddingDimension(
                queryEmbedding,
                storedDimension
        );

        return snapshot.stream()
                .map(entry -> {

                    double score =
                            cosineSimilarity(
                                    queryEmbedding,
                                    entry.embedding()
                            );

                    return RetrievalResult.builder()
                            .document(entry.document())
                            .score(score)
                            .build();
                })
                .sorted(
                        Comparator.comparingDouble(
                                RetrievalResult::getScore
                        ).reversed()
                )
                .limit(topK)
                .toList();
    }

    // ---------------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------------

    private void validate(
            KnowledgeDocument document,
            float[] embedding
    ) {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (document.getId() == null
                || document.getId().isBlank()) {

            throw new IllegalArgumentException(
                    "Document ID must not be blank"
            );
        }

        if (document.getContent() == null) {
            throw new IllegalArgumentException(
                    "Document content must not be null"
            );
        }

        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException(
                    "Embedding must not be null or empty"
            );
        }

        for (float value : embedding) {

            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains a non-finite value"
                );
            }
        }
    }

    /*
     * Validate query vectors separately because
     * search has its own input-validation rules.
     */
    private void validateQueryEmbedding(float[] embedding) {

        for (float value : embedding) {

            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Query embedding contains a non-finite value"
                );
            }
        }
    }

    /*
     * Obtain the dimension used by the current snapshot.
     *
     * This is safe because every published snapshot
     * is now required to have consistent dimensions.
     */
    private Integer getExistingEmbeddingDimension(
            List<VectorEntry> snapshot
    ) {

        if (snapshot.isEmpty()) {
            return null;
        }

        return snapshot.get(0).embedding().length;
    }

    /*
     * If an expected dimension exists, reject any
     * embedding whose dimension does not match it.
     */
    private void validateEmbeddingDimension(
            float[] embedding,
            Integer expectedDimension
    ) {

        if (expectedDimension != null
                && embedding.length != expectedDimension) {

            throw new IllegalArgumentException(
                    "Embedding dimensions must match. Expected: "
                            + expectedDimension
                            + ", received: "
                            + embedding.length
            );
        }
    }

    // ---------------------------------------------------------
    // COSINE SIMILARITY
    // ---------------------------------------------------------

    private double cosineSimilarity(
            float[] vectorA,
            float[] vectorB
    ) {

        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must match"
            );
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {

            double a = vectorA[i];
            double b = vectorB[i];

            dotProduct += a * b;
            magnitudeA += a * a;
            magnitudeB += b * b;
        }

        if (magnitudeA == 0 || magnitudeB == 0) {
            return 0.0;
        }

        return dotProduct /
                (Math.sqrt(magnitudeA)
                        * Math.sqrt(magnitudeB));
    }

    // ---------------------------------------------------------
    // VECTOR ENTRY
    // ---------------------------------------------------------

    private record VectorEntry(
            KnowledgeDocument document,
            float[] embedding
    ) {

        private VectorEntry {
            embedding = embedding.clone();
        }

        /*
         * Prevent callers from modifying the stored
         * embedding through the returned array.
         */
        @Override
        public float[] embedding() {
            return embedding.clone();
        }
    }
}
