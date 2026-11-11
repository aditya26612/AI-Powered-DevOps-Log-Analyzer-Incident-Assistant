
package com.project.llmservice.service.impl;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.mapper.RequestMapper;
import com.project.llmservice.parser.ResponseParser;
import com.project.llmservice.prompt.PromptBuilder;
import com.project.llmservice.prompt.PromptContext;
import com.project.llmservice.prompt.PromptType;
import com.project.llmservice.provider.ProviderFactory;
import com.project.llmservice.provider.LlmProvider;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.Retriever;
import com.project.llmservice.service.LlmAnalysisService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LlmAnalysisServiceImpl implements LlmAnalysisService {

    private static final Logger logger =
            LoggerFactory.getLogger(LlmAnalysisServiceImpl.class);

    private final PromptBuilder promptBuilder;
    private final ProviderFactory providerFactory;
    private final ResponseParser responseParser;
    private final RequestMapper requestMapper;
    private final Retriever retriever;

    public LlmAnalysisServiceImpl(
            PromptBuilder promptBuilder,
            ProviderFactory providerFactory,
            ResponseParser responseParser,
            RequestMapper requestMapper,
            @Qualifier("hybridRetriever") Retriever retriever
    ) {
        this.promptBuilder = promptBuilder;
        this.providerFactory = providerFactory;
        this.responseParser = responseParser;
        this.requestMapper = requestMapper;
        this.retriever = retriever;
    }

    @Override
    public LlmAnalysisResponse analyze(LlmAnalysisRequest request) {

        long startTime = System.currentTimeMillis();

        logger.info("========================================");
        logger.info("LLM ANALYSIS STARTED");
        logger.info("========================================");

        logger.info(
                "Service: {} | Level: {}",
                request.getServiceName(),
                request.getLevel()
        );

        // 1. Convert incoming request into prompt context.
        PromptContext context =
                requestMapper.toPromptContext(request);

        // 2. Construct retrieval query.
        String query =
                request.getLevel() + " " +
                        request.getServiceName() + " " +
                        request.getMessage();

        logger.info(
                "Retrieval query constructed for service: {}",
                request.getServiceName()
        );

        // 3. Retrieve relevant documents using Hybrid Retrieval.
        List<RetrievalResult> results =
                retriever.retrieve(query, 3);

        logger.info(
                "===== RETRIEVAL COMPLETED: {} RESULTS =====",
                results.size()
        );

        // 4. Log retrieved document IDs and scores.
        for (int i = 0; i < results.size(); i++) {

            RetrievalResult result = results.get(i);

            logger.info(
                    "Rank {} | Document: {} | RRF Score: {}",
                    i + 1,
                    result.getDocument().getId(),
                    result.getScore()
            );
        }

        // 5. Combine retrieved document contents.
        String retrievedContext =
                results.stream()
                        .map(result ->
                                result.getDocument().getContent()
                        )
                        .collect(Collectors.joining("\n\n"));

        logger.info("===== RETRIEVED CONTEXT =====");
        logger.info("Context length: {} characters", retrievedContext.length());

        if (retrievedContext.isBlank()) {
            logger.warn("Retrieved context is empty");
        } else {
            logger.info("Retrieved context successfully assembled");
        }

        // 6. Build enriched prompt context.
        PromptContext enrichedContext =
                PromptContext.builder()
                        .timestamp(context.getTimestamp())
                        .level(context.getLevel())
                        .serviceName(context.getServiceName())
                        .message(context.getMessage())
                        .prediction(context.getPrediction())
                        .predictionLabel(context.getPredictionLabel())
                        .decisionScore(context.getDecisionScore())
                        .modelVersion(context.getModelVersion())
                        .retrievedContext(retrievedContext)
                        .build();

        // 7. Generate final prompt.
        String prompt =
                promptBuilder.build(
                        PromptType.ROOT_CAUSE_ANALYSIS,
                        enrichedContext
                );

        logger.info("Final prompt generated successfully");
        logger.info("Prompt length: {} characters", prompt.length());

        // 8. Select LLM provider.
        LlmProvider provider =
                providerFactory.getProvider();

        logger.info(
                "LLM provider selected: {}",
                provider.getClass().getSimpleName()
        );

        // 9. Generate response using the selected provider.
        logger.info("===== SENDING REQUEST TO LLM =====");

        String rawResponse =
                provider.generate(prompt);

        logger.info("LLM response received successfully");

        // 10. Parse LLM response.
        LlmAnalysisResponse response =
                responseParser.parse(rawResponse);

        // 11. Log parsed response fields.
        logger.info("===== PARSED RESPONSE =====");
        logger.info("Summary: {}", response.getSummary());
        logger.info("Root Cause: {}", response.getRootCause());
        logger.info("Severity: {}", response.getSeverity());
        logger.info("Recommendation: {}", response.getRecommendation());

        long duration =
                System.currentTimeMillis() - startTime;

        logger.info(
                "LLM analysis completed successfully in {} ms",
                duration
        );

        logger.info("========================================");

        return response;
    }
}
