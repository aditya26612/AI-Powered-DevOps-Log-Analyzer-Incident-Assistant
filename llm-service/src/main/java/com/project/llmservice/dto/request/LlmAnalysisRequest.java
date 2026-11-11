
package com.project.llmservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Builder
public class LlmAnalysisRequest {

    @NotBlank
    private String timestamp;

    @NotBlank
    private String level;

    @NotBlank
    @JsonProperty("service_name")
    private String serviceName;

    @NotBlank
    private String message;

    private Integer prediction;

    @JsonProperty("prediction_label")
    private String predictionLabel;

    @JsonProperty("decision_score")
    private Double decisionScore;

    @JsonProperty("model_version")
    private String modelVersion;
}
