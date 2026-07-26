package com.project.llmservice.provider;

import com.project.llmservice.properties.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderFactory {

    private final ApplicationProperties applicationProperties;
    private final OllamaProvider ollamaProvider;

    public LlmProvider getProvider() {

        String provider = applicationProperties.getProvider();

        if ("OLLAMA".equalsIgnoreCase(provider)) {
            return ollamaProvider;
        }

        throw new IllegalArgumentException(
                "Unsupported provider: " + provider
        );
    }
}