package com.project.llmservice.rag;

import com.project.llmservice.rag.splitter.DocumentSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SharedDiagnosisRetrieverTest {
    @Test
    void matchesPackagedChunkByBothIdentifierAndContent() {
        ResourceDocumentLoader loader = mock(ResourceDocumentLoader.class);
        DocumentSplitter splitter = mock(DocumentSplitter.class);
        Retriever hybrid = mock(Retriever.class);
        KnowledgeDocument source = doc("runbook.md", "Shared instructions");
        KnowledgeDocument approved = doc("runbook.md-chunk-0", "Shared instructions");
        when(loader.load()).thenReturn(List.of(source));
        when(splitter.split(source)).thenReturn(List.of(approved));
        when(hybrid.retrieve("query", 3)).thenReturn(List.of(
                item(doc("private.md-chunk-0", "Private text")),
                item(doc("runbook.md-chunk-0", "Unverified text")),
                item(approved)));

        assertEquals(List.of(approved), new SharedDiagnosisRetriever(hybrid, loader, splitter)
                .retrieve("query", 3).stream().map(RetrievalResult::getDocument).toList());
    }

    private KnowledgeDocument doc(String id, String text) {
        return KnowledgeDocument.builder().id(id).content(text).build();
    }

    private RetrievalResult item(KnowledgeDocument doc) {
        return RetrievalResult.builder().document(doc).score(0.8).build();
    }
}
