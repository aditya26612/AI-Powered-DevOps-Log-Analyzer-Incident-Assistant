package com.project.log_layer.appintegration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestIntegrationResponse {
    private boolean success;
    private Long logId;
    private String applicationName;
    private String techStack;
    private boolean anomalyDetected;
    private Double anomalyScore;
    private String diagnosisSummary;
    private String remediation;
    private boolean slackDispatched;
    private String slackChannel;
    private long durationMs;
    private String message;
}

