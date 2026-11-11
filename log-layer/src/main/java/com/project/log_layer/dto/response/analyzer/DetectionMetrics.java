package com.project.log_layer.dto.response.analyzer;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionMetrics {

    private Boolean isMlAnomaly;
    private Integer mlPrediction;
    private String mlPredictionLabel;
    private Double decisionScore;
    private String modelVersion;
    private List<String> heuristicFlags;

}

