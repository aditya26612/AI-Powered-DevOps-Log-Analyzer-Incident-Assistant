package com.project.log_layer.integration.client;

import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;

public interface MlInferenceClient {

    MlPredictionResponse predict(
            MlPredictionRequest request
    );

}