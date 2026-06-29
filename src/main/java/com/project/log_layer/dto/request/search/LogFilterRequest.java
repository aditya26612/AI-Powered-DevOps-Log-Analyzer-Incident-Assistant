package com.project.log_layer.dto.request.search;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSortField;
import com.project.log_layer.enums.LogStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO used for searching and filtering logs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogFilterRequest {

    /**
     * Log severity.
     */
    private LogLevel level;

    /**
     * Service name.
     */
    private String serviceName;

    /**
     * Application name.
     */
    private String applicationName;

    /**
     * Deployment environment.
     */
    private Environment environment;

    /**
     * Processing status.
     */
    private LogStatus status;

    /**
     * Search only anomaly logs.
     */
    private Boolean anomaly;

    /**
     * Correlation ID for request tracing.
     */
    private UUID correlationId;

    /**
     * Keyword search inside log message.
     */
    private String keyword;

    /**
     * Search logs generated after this timestamp.
     */
    private LocalDateTime startTime;

    /**
     * Search logs generated before this timestamp.
     */
    private LocalDateTime endTime;

    /**
     * Page number.
     */
    @Min(value = 0, message = "Page number cannot be negative.")
    @Builder.Default
    private Integer page = 0;

    /**
     * Number of records per page.
     */
    @Min(value = 1, message = "Page size must be at least 1.")
    @Max(value = 100, message = "Page size cannot exceed 100.")
    @Builder.Default
    private Integer size = 20;

//    /**
//     * Sorting field.
//     */
//    @Builder.Default
//    private String sortBy = "timestamp";

    @Builder.Default
    private LogSortField sortBy = LogSortField.TIMESTAMP;

    /**
     * Sorting direction.
     */
    @Builder.Default
    private Sort.Direction sortDirection = Sort.Direction.DESC;

}