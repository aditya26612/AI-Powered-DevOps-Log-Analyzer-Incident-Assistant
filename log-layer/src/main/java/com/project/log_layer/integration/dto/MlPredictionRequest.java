package com.project.log_layer.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MlPredictionRequest(

        String timestamp,

        String level,

        @JsonProperty("service_name")
        String serviceName,

        String message

) {
}