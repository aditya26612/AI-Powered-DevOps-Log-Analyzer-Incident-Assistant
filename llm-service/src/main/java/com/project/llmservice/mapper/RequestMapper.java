package com.project.llmservice.mapper;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.prompt.PromptContext;
import org.springframework.stereotype.Component;

@Component
public class RequestMapper {

    public PromptContext toPromptContext(LlmAnalysisRequest request) {

        return PromptContext.builder()
                .timestamp(request.getTimestamp())
                .level(request.getLevel())
                .serviceName(request.getServiceName())
                .message(request.getMessage())
                .prediction(request.getPrediction())
                .predictionLabel(request.getPredictionLabel())
                .decisionScore(request.getDecisionScore())
                .modelVersion(request.getModelVersion())
                .retrievedContext(null)
                .build();
    }
}