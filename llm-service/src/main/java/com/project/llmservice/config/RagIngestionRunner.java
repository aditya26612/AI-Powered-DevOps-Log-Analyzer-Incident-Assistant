package com.project.llmservice.config;

import com.project.llmservice.rag.RagIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagIngestionRunner implements ApplicationRunner {

    private final RagIngestionService ragIngestionService;

    @Value("${rag.synchronize-on-start:true}")
    private boolean synchronizeStorage;

    @Override
    public void run(ApplicationArguments args) {

        if (synchronizeStorage) {
            ragIngestionService.ingest();
        } else {
            ragIngestionService.loadSharedIndex();
            log.info("Existing vector knowledge preserved.");
        }
    }
}