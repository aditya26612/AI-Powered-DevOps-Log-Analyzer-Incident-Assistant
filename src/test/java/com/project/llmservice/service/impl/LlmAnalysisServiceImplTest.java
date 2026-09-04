package com.project.llmservice.service.impl;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.mapper.RequestMapper;
import com.project.llmservice.parser.ResponseParser;
import com.project.llmservice.prompt.PromptBuilder;
import com.project.llmservice.prompt.PromptContext;
import com.project.llmservice.prompt.PromptType;
import com.project.llmservice.provider.LlmProvider;
import com.project.llmservice.provider.ProviderFactory;
import com.project.llmservice.rag.KnowledgeDocument;
import com.project.llmservice.rag.RetrievalResult;
import com.project.llmservice.rag.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmAnalysisServiceImplTest {

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ProviderFactory providerFactory;

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private ResponseParser responseParser;

    @Mock
    private Retriever retriever;

    @Mock
    private RequestMapper requestMapper;

    private LlmAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {

        service = new LlmAnalysisServiceImpl(
                promptBuilder,
                providerFactory,
                responseParser,
                requestMapper,
                retriever
        );
    }

    @Test
    void shouldAnalyzeLogSuccessfully() {

        // --------------------------------------------------
        // Request
        // --------------------------------------------------

        LlmAnalysisRequest request =
                LlmAnalysisRequest.builder()
                        .timestamp("2026-08-27T01:00:00")
                        .level("ERROR")
                        .serviceName("payment-service")
                        .message("Database connection failed")
                        .build();

        // --------------------------------------------------
        // Prompt Context created by RequestMapper
        // --------------------------------------------------

        PromptContext context =
                PromptContext.builder()
                        .timestamp(request.getTimestamp())
                        .level(request.getLevel())
                        .serviceName(request.getServiceName())
                        .message(request.getMessage())
                        .build();

        // --------------------------------------------------
        // Knowledge Document returned by RAG
        // --------------------------------------------------

        KnowledgeDocument knowledgeDocument =
                KnowledgeDocument.builder()
                        .id("database")
                        .content(
                                "PostgreSQL database connectivity should be verified."
                        )
                        .metadata(null)
                        .build();

        // --------------------------------------------------
        // Retrieval Result
        // --------------------------------------------------

        RetrievalResult retrievalResult =
                RetrievalResult.builder()
                        .document(knowledgeDocument)
                        .score(1.0)
                        .build();

        // --------------------------------------------------
        // RequestMapper mock
        // --------------------------------------------------

        when(requestMapper.toPromptContext(request))
                .thenReturn(context);

        // --------------------------------------------------
        // Retriever mock
        // --------------------------------------------------

        when(retriever.retrieve(
                "ERROR payment-service Database connection failed",
                3
        )).thenReturn(List.of(retrievalResult));

        // --------------------------------------------------
        // PromptBuilder mock
        // --------------------------------------------------

        String prompt = "generated prompt";

        when(promptBuilder.build(
                eq(PromptType.ROOT_CAUSE_ANALYSIS),
                any(PromptContext.class)
        )).thenReturn(prompt);

        // --------------------------------------------------
        // LLM raw response
        // --------------------------------------------------

        String rawResponse = """
                {
                    "summary": "Database connection failed.",
                    "rootCause": "PostgreSQL is unreachable.",
                    "severity": "CRITICAL",
                    "recommendation": "Verify database availability."
                }
                """;

        // --------------------------------------------------
        // Expected parsed response
        // --------------------------------------------------

        LlmAnalysisResponse expectedResponse =
                LlmAnalysisResponse.builder()
                        .summary("Database connection failed.")
                        .rootCause("PostgreSQL is unreachable.")
                        .severity("CRITICAL")
                        .recommendation("Verify database availability.")
                        .build();

        // --------------------------------------------------
        // Provider mock
        // --------------------------------------------------

        when(providerFactory.getProvider())
                .thenReturn(llmProvider);

        when(llmProvider.generate(prompt))
                .thenReturn(rawResponse);

        // --------------------------------------------------
        // ResponseParser mock
        // --------------------------------------------------

        when(responseParser.parse(rawResponse))
                .thenReturn(expectedResponse);

        // --------------------------------------------------
        // Execute service
        // --------------------------------------------------

        LlmAnalysisResponse actualResponse =
                service.analyze(request);

        // --------------------------------------------------
        // Verify final response
        // --------------------------------------------------

        assertNotNull(actualResponse);

        assertEquals(
                expectedResponse,
                actualResponse
        );

        // --------------------------------------------------
        // Verify RequestMapper
        // --------------------------------------------------

        verify(requestMapper)
                .toPromptContext(request);

        // --------------------------------------------------
        // Verify Retriever
        // --------------------------------------------------

        verify(retriever)
                .retrieve(
                        "ERROR payment-service Database connection failed",
                        3
                );

        // --------------------------------------------------
        // Capture PromptContext passed to PromptBuilder
        // --------------------------------------------------

        ArgumentCaptor<PromptContext> contextCaptor =
                ArgumentCaptor.forClass(PromptContext.class);

        verify(promptBuilder).build(
                eq(PromptType.ROOT_CAUSE_ANALYSIS),
                contextCaptor.capture()
        );

        PromptContext enrichedContext =
                contextCaptor.getValue();

        // --------------------------------------------------
        // Verify original log information
        // --------------------------------------------------

        assertEquals(
                request.getTimestamp(),
                enrichedContext.getTimestamp()
        );

        assertEquals(
                request.getLevel(),
                enrichedContext.getLevel()
        );

        assertEquals(
                request.getServiceName(),
                enrichedContext.getServiceName()
        );

        assertEquals(
                request.getMessage(),
                enrichedContext.getMessage()
        );

        // --------------------------------------------------
        // Verify RAG retrieved context
        // --------------------------------------------------

        assertEquals(
                "PostgreSQL database connectivity should be verified.",
                enrichedContext.getRetrievedContext()
        );

        // --------------------------------------------------
        // Verify ProviderFactory
        // --------------------------------------------------

        verify(providerFactory)
                .getProvider();

        // --------------------------------------------------
        // Verify LLM Provider
        // --------------------------------------------------

        verify(llmProvider)
                .generate(prompt);

        // --------------------------------------------------
        // Verify ResponseParser
        // --------------------------------------------------

        verify(responseParser)
                .parse(rawResponse);
    }
}