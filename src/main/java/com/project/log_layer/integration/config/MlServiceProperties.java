package com.project.log_layer.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ml.service")
public class MlServiceProperties {

    /**
     * Base URL of the ML Service.
     * Example: http://localhost:8000
     */
    private String baseUrl;

    /**
     * Prediction endpoint.
     * Example: /api/v1/predict
     */
    private String predictEndpoint;

    /**
     * Request timeout.
     * Example: 5s
     */
    private Duration timeout;
}