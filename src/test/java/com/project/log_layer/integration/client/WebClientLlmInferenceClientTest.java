package com.project.log_layer.integration.client;

import com.project.log_layer.integration.config.LlmServiceProperties;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.exception.LlmServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientLlmInferenceClientTest {

    @Test
    void shouldReturnLlmAnalysisResponseWhenRequestSucceeds() {

        LlmServiceProperties properties = new LlmServiceProperties();
        properties.setBaseUrl("http://localhost:8084");
        properties.setAnalyzeEndpoint("/api/v1/llm/analyze");

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        WebClientLlmInferenceClient client =
                new WebClientLlmInferenceClient(webClient, properties);

        LlmAnalysisRequest request = createRequest();

        LlmAnalysisResponse response = client.analyze(request);

        assertEquals(
                "Database connection failed.",
                response.summary()
        );
        assertEquals(
                "PostgreSQL database is unreachable.",
                response.rootCause()
        );
        assertEquals(
                "CRITICAL",
                response.severity()
        );
        assertEquals(
                "Verify database availability, network connectivity, and database credentials.",
                response.recommendation()
        );
    }


    @Test
    void shouldThrowLlmServiceExceptionWhenServiceIsUnavailable() {

        LlmServiceProperties properties = new LlmServiceProperties();
        properties.setBaseUrl("http://localhost:59999");
        properties.setAnalyzeEndpoint("/api/v1/llm/analyze");

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        WebClientLlmInferenceClient client =
                new WebClientLlmInferenceClient(webClient, properties);

        LlmAnalysisRequest request = createRequest();

        LlmServiceException exception = assertThrows(
                LlmServiceException.class,
                () -> client.analyze(request)
        );

        assertEquals(
                "Unable to connect to LLM Service.",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowLlmServiceExceptionWhenServiceReturns500() throws IOException {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/api/v1/llm/analyze",
                exchange -> {

                    String responseBody = "{\"error\":\"Internal server error\"}";

                    exchange.sendResponseHeaders(
                            500,
                            responseBody.getBytes().length
                    );

                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(responseBody.getBytes());
                    }
                }
        );

        server.start();

        try {
            LlmServiceProperties properties = new LlmServiceProperties();
            properties.setBaseUrl(
                    "http://localhost:" + server.getAddress().getPort()
            );
            properties.setAnalyzeEndpoint("/api/v1/llm/analyze");

            WebClient webClient = WebClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .build();

            WebClientLlmInferenceClient client =
                    new WebClientLlmInferenceClient(webClient, properties);

            LlmAnalysisRequest request = createRequest();

            LlmServiceException exception = assertThrows(
                    LlmServiceException.class,
                    () -> client.analyze(request)
            );

            assertEquals(
                    "LLM Service returned server error: {\"error\":\"Internal server error\"}",
                    exception.getMessage()
            );

        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldThrowLlmServiceExceptionWhenServiceReturns400() throws IOException {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/api/v1/llm/analyze",
                exchange -> {

                    String responseBody = "{\"error\":\"Invalid request\"}";

                    exchange.sendResponseHeaders(
                            400,
                            responseBody.getBytes().length
                    );

                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(responseBody.getBytes());
                    }
                }
        );

        server.start();

        try {
            LlmServiceProperties properties = new LlmServiceProperties();
            properties.setBaseUrl(
                    "http://localhost:" + server.getAddress().getPort()
            );
            properties.setAnalyzeEndpoint("/api/v1/llm/analyze");

            WebClient webClient = WebClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .build();

            WebClientLlmInferenceClient client =
                    new WebClientLlmInferenceClient(webClient, properties);

            LlmAnalysisRequest request = createRequest();

            LlmServiceException exception = assertThrows(
                    LlmServiceException.class,
                    () -> client.analyze(request)
            );

            assertEquals(
                    "LLM Service returned client error: {\"error\":\"Invalid request\"}",
                    exception.getMessage()
            );

        } finally {
            server.stop(0);
        }
    }

    private LlmAnalysisRequest createRequest() {
        return LlmAnalysisRequest.builder()
                .timestamp("2026-10-06T00:00:00")
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .prediction(1)
                .predictionLabel("DATABASE_ERROR")
                .decisionScore(0.95)
                .modelVersion("1.0.0")
                .build();
    }
}