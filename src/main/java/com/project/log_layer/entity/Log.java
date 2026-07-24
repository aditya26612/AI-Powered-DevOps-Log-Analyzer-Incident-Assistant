package com.project.log_layer.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.project.log_layer.constant.AppConstants;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.enums.LogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a structured log entry stored in the database.
 *
 * Every supported parser converts raw logs into this unified entity.
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
     * Correlation identifier used for distributed tracing.
     */
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    /**
     * Application generating the log.
     */
    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    /**
     * Time at which the log event occurred.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Log severity.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogLevel level;

    /**
     * Service generating the log.
     */
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    /**
     * Logger class name.
     */
    @Column(name = "logger_name", length = 255)
    private String loggerName;

    /**
     * Thread generating the log.
     */
    @Column(name = "thread_name", length = 100)
    private String threadName;

    /**
     * Parsed log message.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Original raw log.
     */
    @Column(name = "raw_log", nullable = false, columnDefinition = "TEXT")
    private String rawLog;

    /**
     * Source system.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogSource source;

    /**
     * Host / VM / Container / Pod name.
     */
    @Column(name = "host_name", length = 150)
    private String hostName;

    /**
     * Deployment environment.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Environment environment = Environment.DEVELOPMENT;

    /**
     * Current ingestion status.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogStatus status = LogStatus.RECEIVED;

    /**
     * Whether the ML model detected an anomaly.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean anomaly = false;

    /**
     * Legacy anomaly score.
     *
     * NOTE:
     * This field is temporarily retained for backward compatibility.
     * Once the Log Layer is fully integrated with the ML Service,
     * this field will be removed in favor of decisionScore.
     */
    @Builder.Default
    @Column(name = "anomaly_score")
    private Double anomalyScore = 0.0;

    /**
     * Predicted class returned by the ML model.
     *
     * 0 -> Normal
     * 1 -> Anomaly
     */
    @Builder.Default
    @Column(name = "prediction")
    private Integer prediction = 0;

    /**
     * Human-readable prediction label returned by the ML model.
     *
     * Examples:
     * - NORMAL
     * - ANOMALY
     */
    @Builder.Default
    @Column(name = "prediction_label", length = 50)
    private String predictionLabel = "UNKNOWN";

    /**
     * Isolation Forest decision score returned by the ML model.
     *
     * This will become the primary anomaly confidence metric after
     * the legacy anomalyScore field is removed.
     */
    @Builder.Default
    @Column(name = "decision_score")
    private Double decisionScore = 0.0;

    /**
     * Version of the ML model that produced the prediction.
     *
     * Used for auditing and future model comparisons.
     */
    @Builder.Default
    @Column(name = "model_version", length = 30)
    private String modelVersion = "1.0.0";

    /**
     * Current ML analysis lifecycle status.
     *
     * Independent of LogStatus.
     *
     * LogStatus tracks ingestion.
     * AnalysisStatus tracks ML processing.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 30)
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    /**
     * Timestamp when ML analysis completed.
     */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /**
     * Timestamp when the log was persisted.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Automatically sets the creation timestamp before persisting.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}