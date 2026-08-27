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
import com.project.llmservice.service.LlmAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmAnalysisServiceImpl implements LlmAnalysisService {

    private final PromptBuilder promptBuilder;
    private final ProviderFactory providerFactory;
    private final ResponseParser responseParser;
    private final RequestMapper requestMapper;

    @Override
    public LlmAnalysisResponse analyze(LlmAnalysisRequest request) {

        PromptContext context =
                requestMapper.toPromptContext(request);

        String prompt = promptBuilder.build(
                PromptType.ROOT_CAUSE_ANALYSIS,
                context
        );

        LlmProvider provider =
                providerFactory.getProvider();

        String rawResponse =
                provider.generate(prompt);

        return responseParser.parse(rawResponse);
    }
}