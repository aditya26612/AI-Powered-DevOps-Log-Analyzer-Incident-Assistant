package com.project.llmservice.rag;

import com.project.llmservice.rag.splitter.DocumentSplitter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Diagnosis currently uses only release-packaged runbooks. */
@Component("sharedDiagnosisRetriever")
public class SharedDiagnosisRetriever implements Retriever {
    private final Retriever hybrid;
    private final Map<String, String> sharedChunks;

    public SharedDiagnosisRetriever(@Qualifier("hybridRetriever") Retriever hybrid,
                                    ResourceDocumentLoader loader, DocumentSplitter splitter) {
        this.hybrid = hybrid;
        this.sharedChunks = loader.load().stream().flatMap(doc -> splitter.split(doc).stream())
                .collect(Collectors.toUnmodifiableMap(KnowledgeDocument::getId, KnowledgeDocument::getContent));
    }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        // Old rows and unrelated sources can remain stored, but neither metadata alone nor a matching ID grants access.
        return hybrid.retrieve(query, topK).stream()
                .filter(result -> result != null && result.getDocument() != null)
                .filter(result -> {
                    var doc = result.getDocument();
                    return doc.getId() != null && doc.getContent() != null
                            && doc.getContent().equals(sharedChunks.get(doc.getId()));
                }).toList();
    }
}
