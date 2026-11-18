package com.project.log_layer.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({
        MlServiceProperties.class,
        LlmServiceProperties.class
})
public class WebClientConfig {

    @Value("${internal.api-key:devinsight_internal_ml_key_secret_12345}")
    private String internalApiKey;

    @Bean
    public WebClient mlWebClient(
            WebClient.Builder builder,
            MlServiceProperties properties
    ) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", internalApiKey)
                .build();
    }

    @Bean
    public WebClient llmWebClient(
            WebClient.Builder builder,
            LlmServiceProperties properties
    ) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", internalApiKey)
                .build();
    }
}
