package com.project.log_layer.integration.dto;

import lombok.Builder;

@Builder
public record LlmAnalysisResponse(

        String summary,

        String rootCause,

        String severity,

        String recommendation

) {
}