package com.project.log_layer.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import com.project.log_layer.integration.exception.MlServiceException;
import io.micrometer.core.instrument.Metrics;

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
    public void requireConsistentResult() {
        double threshold = switch (modelVersion == null ? "" : modelVersion) {
            case "2.0.0-candidate" -> -0.02;
            case "1.0.0" -> 0.0;
            default -> {
                Metrics.counter("devinsight.log.inference.model_mismatch").increment();
                throw new MlServiceException("Unexpected ML model version");
            }
        };
        if (decisionScore == null || !Double.isFinite(decisionScore) || Math.abs(decisionScore) > 1) {
            throw new MlServiceException("Invalid ML decision score");
        }
        boolean outlier = decisionScore < threshold;
        if (prediction == null || prediction != (outlier ? -1 : 1)
                || anomaly == null || anomaly != outlier
                || !(outlier ? "Anomaly" : "Normal").equalsIgnoreCase(predictionLabel)) {
            throw new MlServiceException("Inconsistent ML prediction");
        }
    }
}
