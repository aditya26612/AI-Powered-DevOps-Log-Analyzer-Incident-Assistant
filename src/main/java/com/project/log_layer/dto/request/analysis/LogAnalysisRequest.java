package com.project.log_layer.dto.request.analysis;

import com.project.log_layer.enums.AnalysisType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;

/**
 * Request DTO for AI-powered log analysis.
 *
 * <p>This request allows clients to configure which AI analyses
 * should be performed on a particular log entry.</p>
 *
 * <p>The log identifier is supplied through the REST path:
 *
 * <pre>
 * POST /api/v1/logs/{id}/analyze
 * </pre>
 *
 * therefore it is intentionally not included in this DTO.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisRequest {

    /**
     * Name of the LLM model to use.
     *
     * Examples:
     * - llama3
     * - mistral
     * - phi4
     */
    @NotBlank(message = "Model name must not be blank.")
    private String model;

    /**
     * Types of AI analysis requested.
     *
     * Example:
     *
     * SUMMARY
     * ROOT_CAUSE
     * SUGGESTED_FIX
     */
    @NotEmpty(message = "At least one analysis type must be selected.")
    private EnumSet<AnalysisType> analysisTypes;

}