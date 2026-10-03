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

    private final PromptBuilder promptBuilder;
    private final ProviderFactory providerFactory;
    private final ResponseParser responseParser;
    private final RequestMapper requestMapper;
    private final Retriever retriever;

    private static final Logger logger =
            LoggerFactory.getLogger(LlmAnalysisServiceImpl.class);

    public LlmAnalysisServiceImpl(
            PromptBuilder promptBuilder,
            ProviderFactory providerFactory,
            ResponseParser responseParser,
            RequestMapper requestMapper,
//            @Qualifier("vectorRetriever") Retriever retriever
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

        logger.info(
                "LLM analysis started: service={}, level={}",
                request.getServiceName(),
                request.getLevel()
        );

        PromptContext context =
                requestMapper.toPromptContext(request);

        String query =
                request.getLevel() + " " +
                        request.getServiceName() + " " +
                        request.getMessage();

        List<RetrievalResult> results =
                retriever.retrieve(query, 3);

        logger.info(
                "RAG retrieval completed: resultCount={}",
                results.size()
        );

        String retrievedContext =
                results.stream()
                        .map(result -> result.getDocument().getContent())
                        .collect(Collectors.joining("\n\n"));

        PromptContext enrichedContext =
                PromptContext.builder()
                        .timestamp(context.getTimestamp())
                        .level(context.getLevel())
                        .serviceName(context.getServiceName())
                        .message(context.getMessage())
                        .retrievedContext(retrievedContext)
                        .build();

        String prompt =
                promptBuilder.build(
                        PromptType.ROOT_CAUSE_ANALYSIS,
                        enrichedContext
                );

        LlmProvider provider =
                providerFactory.getProvider();

        logger.info(
                "LLM provider selected: provider={}",
                provider.getClass().getSimpleName()
        );

        String rawResponse =
                provider.generate(prompt);

        logger.info("LLM generation completed");

        LlmAnalysisResponse response =
                responseParser.parse(rawResponse);

        long duration =
                System.currentTimeMillis() - startTime;

        logger.info(
                "LLM analysis completed: durationMs={}",
                duration
        );

        return response;
    }
}