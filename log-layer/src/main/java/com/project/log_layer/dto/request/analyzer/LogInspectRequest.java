package com.project.log_layer.dto.request.analyzer;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogSource;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

/**
 * Unified request DTO for inspecting and diagnosing a log or stack trace.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogInspectRequest {

    /**
     * Raw log or multi-line stack trace.
     */
    @NotBlank(message = "Log content must not be blank.")
    private String log;

    /**
     * Source / application type (e.g. SPRING_BOOT, DOCKER, KUBERNETES, NGINX, CUSTOM).
     * Defaults to SPRING_BOOT if omitted.
     */
    private LogSource appType;

    /**
     * Deployment environment (DEVELOPMENT, STAGING, PRODUCTION).
     * Defaults to PRODUCTION if omitted.
     */
    private Environment environment;

    /**
     * Name of the application or service.
     */
    private String applicationName;

    /**
     * Host or container name.
     */
    private String hostName;

    /**
     * Optional correlation ID for distributed tracing.
     */
    private UUID correlationId;

    /**
     * Analysis mode:
     * - "AUTO": Runs ML + rules, and runs LLM only if anomaly or error is detected (default).
     * - "FORCE_DEEP": Always runs RAG + LLM root cause analysis.
     * - "FAST": Runs only ML anomaly & rule detection (skips LLM for <15ms return).
     */
    private String analysisMode;

}

