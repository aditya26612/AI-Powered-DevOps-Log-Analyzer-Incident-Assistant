package com.project.log_layer.dto.request.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request DTO for ingesting multiple logs
 * in a single API request.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLogRequest {

    /**
     * Collection of log ingestion requests.
     */
    @Valid
    @NotEmpty(message = "Log collection must not be empty.")
    private List<LogIngestRequest> logs;

}