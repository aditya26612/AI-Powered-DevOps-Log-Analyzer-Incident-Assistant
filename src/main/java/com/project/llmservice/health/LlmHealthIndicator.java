package com.project.llmservice.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("llm")
@RequiredArgsConstructor
public class LlmHealthIndicator implements HealthIndicator {

    private final ProviderHealthChecker providerHealthChecker;

    @Override
    public Health health() {

        if (providerHealthChecker.isHealthy()) {

            return Health.up()
                    .withDetail("provider", "OLLAMA")
                    .build();
        }

        return Health.down()
                .withDetail("provider", "OLLAMA")
                .withDetail("reason", "LLM provider is unavailable")
                .build();
    }
}