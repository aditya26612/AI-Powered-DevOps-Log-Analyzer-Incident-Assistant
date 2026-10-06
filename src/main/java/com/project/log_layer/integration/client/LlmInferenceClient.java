package com.project.log_layer.integration.client;

import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;

public interface LlmInferenceClient {

    LlmAnalysisResponse analyze(
            LlmAnalysisRequest request
    );

}