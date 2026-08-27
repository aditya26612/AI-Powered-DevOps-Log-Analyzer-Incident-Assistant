package com.project.llmservice.health;

import com.project.llmservice.provider.ProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderHealthChecker {

    private final ProviderFactory providerFactory;

    public boolean isHealthy() {

        try {
            providerFactory
                    .getProvider()
                    .generate("Say only the word READY.");

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}