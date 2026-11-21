package com.project.llmservice.health;

import com.project.llmservice.properties.ApplicationProperties;
import com.project.llmservice.properties.OllamaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

@Component
public class ProviderHealthChecker {

    private final boolean ollamaSelected;
    private final String chatModel;
    private final String embeddingModel;
    private final RestClient client;

    public ProviderHealthChecker(ApplicationProperties app, OllamaProperties ollama,
                                 @Value("${spring.ai.ollama.embedding.options.model:}") String embeddingModel) {
        this.ollamaSelected = "OLLAMA".equalsIgnoreCase(app.getProvider());
        this.chatModel = ollama.getChat().getOptions().getModel();
        this.embeddingModel = embeddingModel;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(700));
        requestFactory.setReadTimeout(Duration.ofMillis(700));
        this.client = RestClient.builder().baseUrl(ollama.getBaseUrl()).requestFactory(requestFactory).build();
    }

    public boolean isHealthy() {
        if (!ollamaSelected || chatModel == null || chatModel.isBlank()) {
            return false;
        }
        try {
            var tags = client.get().uri("/api/tags").retrieve().body(OllamaTags.class);
            return tags != null && tags.models() != null
                    && installed(tags.models(), chatModel)
                    && (embeddingModel.isBlank() || installed(tags.models(), embeddingModel));
        } catch (RestClientException e) {
            return false;
        }
    }

    private boolean installed(List<OllamaModel> models, String configured) {
        String name = configured.lastIndexOf(':') <= configured.lastIndexOf('/') ? configured + ":latest" : configured;
        return models.stream().anyMatch(model -> name.equals(model.name()));
    }

    private record OllamaTags(List<OllamaModel> models) {}
    private record OllamaModel(String name) {}
}
