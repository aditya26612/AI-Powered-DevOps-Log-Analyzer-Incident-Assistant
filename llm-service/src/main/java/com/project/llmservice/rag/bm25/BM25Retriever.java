
package com.project.llmservice.rag.bm25;

import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BM25Retriever implements Retriever {

    private final BM25Index bm25Index;

    private volatile boolean initialized = false;

    /*
     * Prepare a candidate without changing the active index.
     */
    public BM25Index.PreparedIndex prepareIndex(
            List<KnowledgeDocument> chunks
    ) {

        if (chunks == null) {
            throw new IllegalArgumentException(
                    "Chunks must not be null or empty"
            );
        }

        return bm25Index.prepare(chunks);
    }

    /*
     * Publish the prepared candidate.
     */
    public void publishIndex(
            BM25Index.PreparedIndex preparedIndex
    ) {

        if (preparedIndex == null) {
            throw new IllegalArgumentException(
                    "Prepared index must not be null"
            );
        }

        bm25Index.publish(preparedIndex);

        initialized = true;

        log.info(
                "BM25 index published successfully with {} chunks",
                preparedIndex.size()
        );
    }

    /*
     * Compatibility method for existing callers.
     */
    public void rebuildIndex(List<KnowledgeDocument> chunks) {

        BM25Index.PreparedIndex preparedIndex =
                prepareIndex(chunks);

        publishIndex(preparedIndex);
    }

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK
    ) {

        if (!initialized
                || query == null
                || query.isBlank()
                || topK <= 0) {

            return List.of();
        }

        return bm25Index.search(query, topK);
    }
}
