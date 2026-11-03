package com.project.llmservice.config;

import com.project.llmservice.rag.RagIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagIngestionRunner implements ApplicationRunner {

    private final RagIngestionService ragIngestionService;

    @Override
    public void run(ApplicationArguments args) {

        log.info("Starting RAG knowledge base ingestion...");

        ragIngestionService.ingest();

        log.info("RAG knowledge base ingestion completed.");
    }
}