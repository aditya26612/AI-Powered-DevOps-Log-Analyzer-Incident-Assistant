package com.project.llmservice.health;

import com.project.llmservice.properties.ApplicationProperties;
import com.project.llmservice.properties.OllamaProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderHealthCheckerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readinessRequiresConfiguredModelsWithoutCallingInference(boolean embeddingInstalled) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            String embedding = embeddingInstalled ? ",{\"name\":\"nomic-embed-text:latest\"}" : "";
            byte[] response = ("{\"models\":[{\"name\":\"phi3:mini\"}" + embedding + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            var app = new ApplicationProperties();
            app.setProvider("OLLAMA");
            var ollama = new OllamaProperties();
            ollama.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            ollama.getChat().getOptions().setModel("phi3:mini");
            assertEquals(embeddingInstalled, new ProviderHealthChecker(app, ollama, "nomic-embed-text").isHealthy());
        } finally {
            server.stop(0);
        }
    }
}
