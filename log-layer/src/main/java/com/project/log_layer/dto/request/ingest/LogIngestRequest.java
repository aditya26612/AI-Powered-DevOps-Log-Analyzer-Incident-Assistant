package com.project.log_layer.dto.request.ingest;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Request DTO for ingesting a single raw log.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogIngestRequest {

    /**
     * Original raw log received from the client.
     */
    @NotBlank(message = "Raw log must not be empty.")
    private String rawLog;

    /**
     * Source of the log.
     */
    @NotNull(message = "Log source is required.")
    private LogSource source;

    /**
     * Deployment environment.
     */
    @NotNull(message = "Environment is required.")
    private Environment environment;

    /**
     * Spring Boot application name.
     */
    @NotBlank(message = "Application name is required.")
    private String applicationName;

    /**
     * Host name or container name.
     */
    private String hostName;

    /**
     * Correlation identifier.
     *
     * Optional.
     * If not provided, the Log Service generates a new UUID.
     */
    private UUID correlationId;

}