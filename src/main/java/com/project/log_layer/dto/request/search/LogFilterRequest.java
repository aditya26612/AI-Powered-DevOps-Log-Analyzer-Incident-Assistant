package com.project.log_layer.dto.request.search;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSortField;
import com.project.log_layer.enums.LogSource;
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
 *
 * <p>
 * All fields are optional except pagination defaults.
 * Only the supplied fields are applied as search filters.
 * </p>
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
     * Source of the log.
     *
     * Example:
     * SPRING_BOOT
     * DOCKER
     * KUBERNETES
     * NGINX
     */
    private LogSource source;

    /**
     * Deployment environment.
     */
    private Environment environment;

    /**
     * Current processing status.
     */
    private LogStatus status;

    /**
     * Filter only anomaly logs.
     */
    private Boolean anomaly;

    /**
     * Spring Boot application name.
     */
    private String applicationName;

    /**
     * Service name.
     */
    private String serviceName;

    /**
     * Logger name.
     *
     * Example:
     * com.project.user.UserService
     */
    private String loggerName;

    /**
     * Thread name.
     *
     * Example:
     * main
     * http-nio-8080-exec-1
     */
    private String threadName;

    /**
     * Host or container name.
     */
    private String hostName;

    /**
     * Search text inside the log message.
     *
     * Performs a partial match.
     */
    private String message;

    /**
     * Correlation identifier.
     *
     * Useful for distributed tracing.
     */
    private UUID correlationId;

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

    /**
     * Field used for sorting.
     */
    @Builder.Default
    private LogSortField sortBy = LogSortField.TIMESTAMP;

    /**
     * Sorting direction.
     */
    @Builder.Default
    private Sort.Direction sortDirection = Sort.Direction.DESC;

}