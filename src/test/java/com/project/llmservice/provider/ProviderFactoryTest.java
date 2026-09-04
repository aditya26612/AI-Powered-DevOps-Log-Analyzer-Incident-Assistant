package com.project.llmservice.provider;

import com.project.llmservice.properties.ApplicationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderFactoryTest {

    @Test
    void shouldReturnOllamaProviderWhenConfigured() {

        ApplicationProperties properties =
                new ApplicationProperties();

        properties.setProvider("OLLAMA");

        OllamaProvider ollamaProvider =
                mock(OllamaProvider.class);

        ProviderFactory factory =
                new ProviderFactory(
                        properties,
                        ollamaProvider
                );

        LlmProvider result = factory.getProvider();

        assertNotNull(result);
        assertSame(ollamaProvider, result);
    }

    @Test
    void shouldSupportCaseInsensitiveProviderName() {

        ApplicationProperties properties =
                new ApplicationProperties();

        properties.setProvider("ollama");

        OllamaProvider ollamaProvider =
                mock(OllamaProvider.class);

        ProviderFactory factory =
                new ProviderFactory(
                        properties,
                        ollamaProvider
                );

        LlmProvider result = factory.getProvider();

        assertSame(ollamaProvider, result);
    }

    @Test
    void shouldRejectUnsupportedProvider() {

        ApplicationProperties properties =
                new ApplicationProperties();

        properties.setProvider("OPENAI");

        OllamaProvider ollamaProvider =
                mock(OllamaProvider.class);

        ProviderFactory factory =
                new ProviderFactory(
                        properties,
                        ollamaProvider
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        factory::getProvider
                );

        assertTrue(
                exception.getMessage()
                        .contains("Unsupported provider")
        );
    }
}