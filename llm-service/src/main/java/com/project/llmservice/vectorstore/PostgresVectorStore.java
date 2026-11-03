
package com.project.llmservice.vectorstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;


@ConditionalOnProperty(
        name = "rag.vector-store",
        havingValue = "postgres",
        matchIfMissing = true
)
@Repository
@RequiredArgsConstructor
public class PostgresVectorStore implements VectorStore {

    private static final int EMBEDDING_DIMENSION = 768;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void add(KnowledgeDocument document, float[] embedding) {

        validate(document, embedding);

        String sql = """
                INSERT INTO knowledge_chunks
                    (document_id, content, metadata, embedding)
                VALUES (?, ?, ?::jsonb, ?::vector)
                ON CONFLICT (document_id)
                DO UPDATE SET
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding
                """;

        jdbcTemplate.update(
                sql,
                document.getId(),
                document.getContent(),
                serializeMetadata(document.getMetadata()),
                toVectorLiteral(embedding)
        );
    }

    @Override
    @Transactional
    public void synchronize(
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

        Set<String> currentChunkIds = new LinkedHashSet<>();
        Set<String> representedSourceIds = new HashSet<>();

        /*
         * Validate the complete batch before modifying PostgreSQL.
         */
        for (EmbeddedDocument embeddedDocument : documents) {

            if (embeddedDocument == null) {
                throw new IllegalArgumentException(
                        "Embedded document must not be null"
                );
            }

            KnowledgeDocument document = embeddedDocument.document();
            float[] embedding = embeddedDocument.embedding();

            validate(document, embedding);

            if (!currentChunkIds.add(document.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate chunk ID: " + document.getId()
                );
            }

            Map<String, String> metadata = document.getMetadata();

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
         * Do not delete old data if any managed source
         * has no corresponding current chunks.
         */
        if (!representedSourceIds.equals(sourceIds)) {
            throw new IllegalArgumentException(
                    "Every managed source document must have at least one chunk"
            );
        }

        String upsertSql = """
            INSERT INTO knowledge_chunks
                (document_id, content, metadata, embedding)
            VALUES (?, ?, ?::jsonb, ?::vector)
            ON CONFLICT (document_id)
            DO UPDATE SET
                content = EXCLUDED.content,
                metadata = EXCLUDED.metadata,
                embedding = EXCLUDED.embedding
            """;

        /*
         * Step 1: Upsert every current chunk in a batch.
         */
        jdbcTemplate.batchUpdate(
                upsertSql,
                documents,
                documents.size(),
                (preparedStatement, embeddedDocument) -> {

                    KnowledgeDocument document =
                            embeddedDocument.document();

                    preparedStatement.setString(
                            1,
                            document.getId()
                    );

                    preparedStatement.setString(
                            2,
                            document.getContent()
                    );

                    preparedStatement.setString(
                            3,
                            serializeMetadata(document.getMetadata())
                    );

                    preparedStatement.setString(
                            4,
                            toVectorLiteral(embeddedDocument.embedding())
                    );
                }
        );

        /*
         * Step 2: Delete obsolete chunks, but only
         * for the source documents managed by this ingestion.
         */
        String sourcePlaceholders =
                String.join(
                        ",",
                        java.util.Collections.nCopies(sourceIds.size(), "?")
                );

        String chunkPlaceholders =
                String.join(
                        ",",
                        java.util.Collections.nCopies(
                                currentChunkIds.size(),
                                "?"
                        )
                );

        String deleteSql = """
            DELETE FROM knowledge_chunks
            WHERE metadata->>'sourceDocumentId' IN (%s)
              AND document_id NOT IN (%s)
            """.formatted(sourcePlaceholders, chunkPlaceholders);

        List<Object> parameters = new ArrayList<>();

        parameters.addAll(sourceIds);
        parameters.addAll(currentChunkIds);

        jdbcTemplate.update(
                deleteSql,
                parameters.toArray()
        );
    }

    @Override
    public List<RetrievalResult> search(float[] queryEmbedding, int topK) {

        if (queryEmbedding == null ||
                queryEmbedding.length != EMBEDDING_DIMENSION ||
                topK <= 0) {
            return List.of();
        }

        String sql = """
                SELECT
                    document_id,
                    content,
                    metadata,
                    1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_chunks
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        String vector = toVectorLiteral(queryEmbedding);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapResult(rs),
                vector,
                vector,
                topK
        );
    }

    private RetrievalResult mapResult(ResultSet rs) throws SQLException {

        String documentId = rs.getString("document_id");
        String content = rs.getString("content");

        Map<String, String> metadata =
                deserializeMetadata(rs.getString("metadata"));

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id(documentId)
                .content(content)
                .metadata(metadata)
                .build();

        return RetrievalResult.builder()
                .document(document)
                .score(rs.getDouble("similarity"))
                .build();
    }

    private void validate(
            KnowledgeDocument document,
            float[] embedding
    ) {
        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        if (document.getId() == null || document.getId().isBlank()) {
            throw new IllegalArgumentException(
                    "Document ID must not be blank"
            );
        }

        if (document.getContent() == null) {
            throw new IllegalArgumentException(
                    "Document content must not be null"
            );
        }

        if (embedding == null ||
                embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "Embedding must contain exactly 768 dimensions"
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

    private String toVectorLiteral(float[] embedding) {

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < embedding.length; i++) {

            if (!Float.isFinite(embedding[i])) {
                throw new IllegalArgumentException(
                        "Embedding contains a non-finite value"
                );
            }

            if (i > 0) {
                builder.append(",");
            }

            builder.append(embedding[i]);
        }

        return builder.append("]").toString();
    }

    private String serializeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(
                    metadata == null ? Map.of() : metadata
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize document metadata",
                    exception
            );
        }
    }

    private Map<String, String> deserializeMetadata(String metadata) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return Map.of();
            }

            return objectMapper.readValue(
                    metadata,
                    new TypeReference<Map<String, String>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to deserialize document metadata",
                    exception
            );
        }
    }
}
