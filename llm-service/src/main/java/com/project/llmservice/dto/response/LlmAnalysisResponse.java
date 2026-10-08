package com.project.llmservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAnalysisResponse {

    private String summary;

    private String rootCause;

    private String severity;

    private String recommendation;
}