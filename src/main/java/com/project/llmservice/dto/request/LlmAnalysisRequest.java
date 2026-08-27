package com.project.llmservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmAnalysisRequest {

    @NotBlank
    private String timestamp;

    @NotBlank
    private String level;

    @NotBlank
    private String serviceName;

    @NotBlank
    private String message;
}