package com.project.log_layer.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MlPredictionResponse(

        Integer prediction,

        @JsonProperty("prediction_label")
        String predictionLabel,

        @JsonProperty("is_anomaly")
        Boolean anomaly,

        @JsonProperty("decision_score")
        Double decisionScore,

        @JsonProperty("model_version")
        String modelVersion

) {
}