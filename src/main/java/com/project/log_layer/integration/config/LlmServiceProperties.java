package com.project.log_layer.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "llm.service")
public class LlmServiceProperties {

    /**
     * Base URL of the LLM Service.
     * Example: http://localhost:8084
     */
    private String baseUrl;

    /**
     * LLM analysis endpoint.
     * Example: /api/v1/llm/analyze
     */
    private String analyzeEndpoint;

    /**
     * Request timeout.
     */
    private Duration timeout;
}