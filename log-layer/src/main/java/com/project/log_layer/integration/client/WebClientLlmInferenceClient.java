package com.project.log_layer.integration.client;

import com.project.log_layer.integration.config.LlmServiceProperties;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.exception.LlmServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class WebClientLlmInferenceClient implements LlmInferenceClient {

    private final WebClient llmWebClient;
    private final LlmServiceProperties llmServiceProperties;

    public WebClientLlmInferenceClient(
            @Qualifier("llmWebClient") WebClient llmWebClient,
            LlmServiceProperties llmServiceProperties
    ) {
        this.llmWebClient = llmWebClient;
        this.llmServiceProperties = llmServiceProperties;
    }

    @Override
    public LlmAnalysisResponse analyze(LlmAnalysisRequest request) {

        try {

            return llmWebClient
                    .post()
                    .uri(llmServiceProperties.getAnalyzeEndpoint())
                    .bodyValue(request)
                    .retrieve()

                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new LlmServiceException(
                                            "LLM Service returned client error: " + body))
                    )

                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new LlmServiceException(
                                            "LLM Service returned server error: " + body))
                    )

                    .bodyToMono(LlmAnalysisResponse.class)
                    .block();

        } catch (LlmServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmServiceException(
                    "Unable to connect to LLM Service.",
                    ex
            );
        }
    }
}