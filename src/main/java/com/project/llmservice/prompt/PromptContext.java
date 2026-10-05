package com.project.llmservice.prompt;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromptContext {

    private final String timestamp;

    private final String level;

    private final String serviceName;

    private final String message;

    private final Integer prediction;

    private final String predictionLabel;

    private final Double decisionScore;

    private final String modelVersion;

    private final String retrievedContext;
}