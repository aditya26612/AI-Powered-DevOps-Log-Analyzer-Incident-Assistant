package com.project.log_layer.dto.response.analyzer;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogInspectionResponse {

    private UUID correlationId;
    private LogSource appType;
    private String applicationName;
    private Environment environment;
    private LocalDateTime timestamp;
    private LogLevel level;
    private String message;
    private boolean hasStackTrace;
    private String stackTrace;

    private boolean hasAnomaly;
    private Double confidenceScore;

    private DetectionMetrics detectionMetrics;
    private IncidentDiagnosis incidentDiagnosis;

    private Long executionTimeMs;
    private Map<String, String> metadata;

}

