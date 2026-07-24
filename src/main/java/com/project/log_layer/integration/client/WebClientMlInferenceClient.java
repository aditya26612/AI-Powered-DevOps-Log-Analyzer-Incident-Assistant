package com.project.log_layer.integration.client;

import com.project.log_layer.integration.config.MlServiceProperties;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.integration.exception.MlServiceException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WebClientMlInferenceClient implements MlInferenceClient {

    private final WebClient mlWebClient;
    private final MlServiceProperties mlServiceProperties;

    public WebClientMlInferenceClient(
            WebClient mlWebClient,
            MlServiceProperties mlServiceProperties
    ) {
        this.mlWebClient = mlWebClient;
        this.mlServiceProperties = mlServiceProperties;
    }

//    @Override
//    public MlPredictionResponse predict(MlPredictionRequest request) {
//        throw new UnsupportedOperationException("Not implemented yet");
//    }
@Override
public MlPredictionResponse predict(MlPredictionRequest request) {

    return mlWebClient
            .post()
            .uri(mlServiceProperties.getPredictEndpoint())
            .bodyValue(request)
            .retrieve()

            .onStatus(
                    HttpStatusCode::is4xxClientError,
                    response -> response.bodyToMono(String.class)
                            .map(body ->
                                    new MlServiceException(
                                            "ML Service returned client error: " + body
                                    )
                            )
            )

            .onStatus(
                    HttpStatusCode::is5xxServerError,
                    response -> response.bodyToMono(String.class)
                            .map(body ->
                                    new MlServiceException(
                                            "ML Service returned server error: " + body
                                    )
                            )
            )

            .bodyToMono(MlPredictionResponse.class)
            .block();
}

}