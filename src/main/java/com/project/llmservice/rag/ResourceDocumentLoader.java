package com.project.llmservice.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ResourceDocumentLoader implements DocumentLoader {

    private static final String KNOWLEDGE_PATH =
            "classpath:/knowledge/*.md";

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    @Override
    public List<KnowledgeDocument> load() {

        try {
            Resource[] resources =
                    resolver.getResources(KNOWLEDGE_PATH);

            List<KnowledgeDocument> documents =
                    new ArrayList<>();

            for (Resource resource : resources) {

                String content = new String(
                        resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                if (content.isBlank()) {
                    continue;
                }

                documents.add(
                        KnowledgeDocument.builder()
                                .id(resource.getFilename())
                                .content(content)
                                .metadata(java.util.Map.of(
                                        "source",
                                        resource.getFilename()
                                ))
                                .build()
                );
            }

            return documents;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to load knowledge documents",
                    e
            );
        }
    }
}