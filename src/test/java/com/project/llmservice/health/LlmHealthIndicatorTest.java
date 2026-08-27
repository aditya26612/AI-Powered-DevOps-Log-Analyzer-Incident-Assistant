package com.project.llmservice.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LlmHealthIndicatorTest {

    private ProviderHealthChecker providerHealthChecker;

    private LlmHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        providerHealthChecker = mock(ProviderHealthChecker.class);
        healthIndicator = new LlmHealthIndicator(providerHealthChecker);
    }

    @Test
    void shouldReturnUpWhenProviderIsHealthy() {

        when(providerHealthChecker.isHealthy())
                .thenReturn(true);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(
                "OLLAMA",
                health.getDetails().get("provider")
        );

        verify(providerHealthChecker).isHealthy();
    }

    @Test
    void shouldReturnDownWhenProviderIsUnavailable() {

        when(providerHealthChecker.isHealthy())
                .thenReturn(false);

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(
                "OLLAMA",
                health.getDetails().get("provider")
        );
        assertEquals(
                "LLM provider is unavailable",
                health.getDetails().get("reason")
        );

        verify(providerHealthChecker).isHealthy();
    }
}