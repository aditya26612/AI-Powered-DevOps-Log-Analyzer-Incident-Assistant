package com.project.log_layer.entity;

import com.project.log_layer.constant.AppConstants;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.enums.LogStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a structured log entry stored in the database.
 *
 * Every supported parser (Spring Boot, Docker, Nginx, etc.)
 * converts raw logs into this unified entity.
 *
 * This entity serves as the central model for:
 * - Log Ingestion
 * - Log Search
 * - ML Anomaly Detection
 * - LLM Analysis
 * - Dashboard Analytics
 * - Distributed Tracing
 */
@Entity
@Table(name = AppConstants.LOG_TABLE)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {

    /**
     * Unique database identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier shared across multiple microservices
     * for tracing a single request.
     */
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    /**
     * Name of the application generating the log.
     * Example: user-service
     */
    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    /**
     * Time at which the log event actually occurred.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Severity level of the log.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogLevel level;

    /**
     * Name of the service or component generating the log.
     */
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    /**
     * Fully qualified logger/class name.
     */
    @Column(name = "logger_name", length = 255)
    private String loggerName;

    /**
     * Thread responsible for generating the log.
     */
    @Column(name = "thread_name", length = 100)
    private String threadName;

    /**
     * Parsed log message.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Original unparsed log entry.
     */
    @Column(name = "raw_log", nullable = false, columnDefinition = "TEXT")
    private String rawLog;

    /**
     * Source from which the log originated.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogSource source;

    /**
     * Host, VM, container or node generating the log.
     */
    @Column(name = "host_name", length = 150)
    private String hostName;

//    /**
//     * Deployment environment.
//     */
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false, length = 30)
//    private Environment environment;
//
//    /**
//     * Current processing state inside DevInsight.
//     */
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false, length = 30)
//    private LogStatus status;
//
//    /**
//     * Whether the ML model marked this log as anomalous.
//     */
//    @Builder.Default
//    @Column(nullable = false)
//    private Boolean anomaly = false;
//
//    /**
//     * ML anomaly confidence score.
//     */
//    @Builder.Default
//    @Column(name = "anomaly_score", nullable = false)
//    private Double anomalyScore = 0.0;


    /**
     * Deployment environment.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Environment environment = Environment.DEVELOPMENT;

    /**
     * Current processing state inside DevInsight.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogStatus status = LogStatus.RECEIVED;

    /**
     * Whether the ML model marked this log as anomalous.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean anomaly = false;

    /**
     * ML anomaly confidence score.
     */
    @Builder.Default
    @Column(name = "anomaly_score", nullable = false)
    private Double anomalyScore = 0.0;

    /**
     * Timestamp when the log was stored.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Automatically sets the creation timestamp
     * before persisting the entity.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}