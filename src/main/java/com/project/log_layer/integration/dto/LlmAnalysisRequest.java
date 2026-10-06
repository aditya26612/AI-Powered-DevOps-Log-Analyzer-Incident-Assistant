package com.project.log_layer.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record LlmAnalysisRequest(

        String timestamp,

        String level,

        @JsonProperty("service_name")
        String serviceName,

        String message,

        Integer prediction,

        @JsonProperty("prediction_label")
        String predictionLabel,

        @JsonProperty("decision_score")
        Double decisionScore,

        @JsonProperty("model_version")
        String modelVersion

) {
}