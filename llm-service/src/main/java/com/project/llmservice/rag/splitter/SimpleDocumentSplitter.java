
package com.project.llmservice.rag.splitter;

import com.project.llmservice.rag.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimpleDocumentSplitter implements DocumentSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    /*
     * Boundary priority:
     * 1. Paragraph boundary
     * 2. Sentence boundary
     * 3. Word boundary
     * 4. Hard character boundary
     */

    private static final Pattern PARAGRAPH_BOUNDARY =
            Pattern.compile("\\R[\\t ]*\\R+");

    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[.!?])[\\t ]+");

    private static final Pattern WORD_BOUNDARY =
            Pattern.compile("\\s+");

    @Override
    public List<KnowledgeDocument> split(KnowledgeDocument document) {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document must not be null"
            );
        }

        String content = document.getContent();

        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<KnowledgeDocument> chunks = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;

        while (start < content.length()) {

            int targetEnd = Math.min(
                    start + DEFAULT_CHUNK_SIZE,
                    content.length()
            );

            int end;

            /*
             * If the remaining content fits in one chunk,
             * take it completely without unnecessary boundary searching.
             */
            if (targetEnd == content.length()) {
                end = content.length();
            } else {
                end = findBestBoundary(
                        content,
                        start,
                        targetEnd
                );
            }

            String chunkContent = content.substring(start, end);

            chunks.add(
                    createChunk(
                            document,
                            chunkContent,
                            chunkIndex
                    )
            );

            if (end == content.length()) {
                break;
            }

            /*
             * The next chunk starts before the previous chunk ends.
             * This preserves context between consecutive chunks.
             */
            int nextStart = end - DEFAULT_CHUNK_OVERLAP;

            /*
             * Safety check: guarantee forward progress.
             */
            if (nextStart <= start) {
                nextStart = end;
            }

            start = nextStart;
            chunkIndex++;
        }

        return chunks;
    }

    private int findBestBoundary(
            String content,
            int start,
            int targetEnd
    ) {

        /*
         * Avoid choosing a boundary too close to the beginning
         * of the current chunk.
         *
         * At least half of the target chunk size should be used
         * before considering an earlier natural boundary.
         */
        int minimumEnd = Math.min(
                start + (DEFAULT_CHUNK_SIZE / 2),
                targetEnd
        );

        /*
         * Priority 1: paragraph boundary.
         */
        int boundary = findLastBoundary(
                PARAGRAPH_BOUNDARY,
                content,
                minimumEnd,
                targetEnd
        );

        if (boundary != -1) {
            return boundary;
        }

        /*
         * Priority 2: sentence boundary.
         */
        boundary = findLastBoundary(
                SENTENCE_BOUNDARY,
                content,
                minimumEnd,
                targetEnd
        );

        if (boundary != -1) {
            return boundary;
        }

        /*
         * Priority 3: word boundary.
         */
        boundary = findLastBoundary(
                WORD_BOUNDARY,
                content,
                minimumEnd,
                targetEnd
        );

        if (boundary != -1) {
            return boundary;
        }

        /*
         * Priority 4: hard character boundary.
         *
         * Used when no natural boundary exists,
         * such as a very long unbroken string.
         */
        return targetEnd;
    }

    private int findLastBoundary(
            Pattern pattern,
            String content,
            int minimumEnd,
            int targetEnd
    ) {

        Matcher matcher = pattern.matcher(content);

        int lastBoundary = -1;

        /*
         * Search only within the allowed boundary window.
         */
        matcher.region(minimumEnd, targetEnd);

        while (matcher.find()) {
            lastBoundary = matcher.end();
        }

        return lastBoundary;
    }

    private KnowledgeDocument createChunk(
            KnowledgeDocument document,
            String chunkContent,
            int chunkIndex
    ) {

        /*
         * Preserve metadata from the original document.
         */
        Map<String, String> metadata = new HashMap<>();

        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }

        /*
         * Add chunk-specific metadata.
         * These generated values take precedence over
         * any existing values with the same keys.
         */
        metadata.put(
                "sourceDocumentId",
                document.getId()
        );

        metadata.put(
                "chunkIndex",
                String.valueOf(chunkIndex)
        );

        return KnowledgeDocument.builder()
                .id(document.getId() + "-chunk-" + chunkIndex)
                .content(chunkContent)
                .metadata(metadata)
                .build();
    }
}
