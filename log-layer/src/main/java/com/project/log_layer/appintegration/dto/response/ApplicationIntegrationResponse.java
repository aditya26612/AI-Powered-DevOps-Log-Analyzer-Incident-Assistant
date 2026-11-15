package com.project.log_layer.appintegration.dto.response;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationIntegrationResponse {

    private Long id;
    private String applicationName;
    private TechStack techStack;
    private Environment environment;
    private String apiKey;
    private String slackWebhookUrl;
    private String slackChannelName;
    private Boolean alertOnAnomaly;
    private Boolean alertOnError;
    private Double anomalyThreshold;
    private String description;
    private String userEmail;
    private String codeSnippet;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

