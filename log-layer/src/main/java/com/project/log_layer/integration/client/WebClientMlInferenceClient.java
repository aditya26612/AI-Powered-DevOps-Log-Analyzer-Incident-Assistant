package com.project.log_layer.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.log_layer.integration.config.MlServiceProperties;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.integration.exception.MlServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import reactor.core.publisher.Mono;

@Service
public class WebClientMlInferenceClient implements MlInferenceClient {

    private final WebClient mlWebClient;
    private final MlServiceProperties mlServiceProperties;
    private final Semaphore slots = new Semaphore(4);

    public WebClientMlInferenceClient(
            @Qualifier("mlWebClient") WebClient mlWebClient,
            MlServiceProperties mlServiceProperties
    ) {
        this.mlWebClient = mlWebClient;
        this.mlServiceProperties = mlServiceProperties;
    }

    @Override
    public MlPredictionResponse predict(MlPredictionRequest request) {
        if (!slots.tryAcquire()) {
            throw new MlServiceException("ML client at capacity");
        }
        try {
            return mlWebClient
                    .post()
                    .uri(mlServiceProperties.getPredictEndpoint())
                    .bodyValue(request)
                    .retrieve()

                    .onStatus(status -> !status.is2xxSuccessful(),
                            response -> Mono.just(new MlServiceException("ML HTTP " + response.statusCode().value())))
                    .bodyToMono(JsonNode.class)
                    .map(WebClientMlInferenceClient::readPrediction)
                    .timeout(mlServiceProperties.getTimeout() != null
                            ? mlServiceProperties.getTimeout()
                            : Duration.ofSeconds(5))
                    .block();

        } catch (MlServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Do not retain WebClient error bodies, request metadata, or malformed JSON in the cause.
            throw new MlServiceException("ML transport or decoding failure");
        } finally {
            slots.release();
        }
    }

    static MlPredictionResponse readPrediction(JsonNode body) {
        JsonNode prediction = body.path("prediction");
        JsonNode label = body.path("prediction_label");
        JsonNode anomaly = body.path("is_anomaly");
        JsonNode score = body.path("decision_score");
        JsonNode version = body.path("model_version");
        if (!prediction.isIntegralNumber() || !prediction.canConvertToInt() || !label.isTextual()
                || !anomaly.isBoolean() || !score.isNumber() || !version.isTextual()) {
            throw new MlServiceException("Invalid ML response field types");
        }
        return new MlPredictionResponse(prediction.intValue(), label.textValue(), anomaly.booleanValue(),
                score.doubleValue(), version.textValue());
    }
}
