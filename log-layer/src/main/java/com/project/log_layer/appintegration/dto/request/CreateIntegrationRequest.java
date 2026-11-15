package com.project.log_layer.appintegration.dto.request;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIntegrationRequest {

    @NotBlank(message = "Application name is required")
    @Size(min = 2, max = 100, message = "Application name must be between 2 and 100 characters")
    private String applicationName;

    @NotNull(message = "Technology stack is required")
    private TechStack techStack;

    @NotNull(message = "Environment is required")
    private Environment environment;

    private String slackWebhookUrl;

    private String slackChannelName;

    @Builder.Default
    private Boolean alertOnAnomaly = true;

    @Builder.Default
    private Boolean alertOnError = true;

    @Builder.Default
    private Double anomalyThreshold = -0.1;

    private String description;
}

