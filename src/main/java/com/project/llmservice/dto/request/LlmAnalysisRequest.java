package com.project.llmservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LlmAnalysisRequest {

    @NotBlank
    private String timestamp;

    @NotBlank
    private String level;

    @NotBlank
    private String serviceName;

    @NotBlank
    private String message;

    private Integer prediction;

    private String predictionLabel;

    private Double decisionScore;

    private String modelVersion;
}