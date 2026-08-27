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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private RequestMapper requestMapper;

    private LlmAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmAnalysisServiceImpl(
                promptBuilder,
                providerFactory,
                responseParser,
                requestMapper
        );
    }

    @Test
    void shouldAnalyzeLogSuccessfully() {

        LlmAnalysisRequest request = LlmAnalysisRequest.builder()
                .timestamp("2026-08-27T01:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .build();

        PromptContext context = PromptContext.builder()
                .timestamp(request.getTimestamp())
                .level(request.getLevel())
                .serviceName(request.getServiceName())
                .message(request.getMessage())
                .build();

        String prompt = "generated prompt";

        String rawResponse = """
                {
                    "summary": "Database connection failed.",
                    "rootCause": "PostgreSQL is unreachable.",
                    "severity": "CRITICAL",
                    "recommendation": "Verify database availability."
                }
                """;

        LlmAnalysisResponse expectedResponse =
                LlmAnalysisResponse.builder()
                        .summary("Database connection failed.")
                        .rootCause("PostgreSQL is unreachable.")
                        .severity("CRITICAL")
                        .recommendation("Verify database availability.")
                        .build();

        when(requestMapper.toPromptContext(request))
                .thenReturn(context);

        when(promptBuilder.build(
                PromptType.ROOT_CAUSE_ANALYSIS,
                context
        )).thenReturn(prompt);

        when(providerFactory.getProvider())
                .thenReturn(llmProvider);

        when(llmProvider.generate(prompt))
                .thenReturn(rawResponse);

        when(responseParser.parse(rawResponse))
                .thenReturn(expectedResponse);

        LlmAnalysisResponse actualResponse =
                service.analyze(request);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(requestMapper)
                .toPromptContext(request);

        verify(promptBuilder).build(
                PromptType.ROOT_CAUSE_ANALYSIS,
                context
        );

        verify(providerFactory)
                .getProvider();

        verify(llmProvider)
                .generate(prompt);

        verify(responseParser)
                .parse(rawResponse);
    }
}