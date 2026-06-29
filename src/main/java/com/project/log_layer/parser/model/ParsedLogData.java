package com.project.log_layer.parser.model;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Internal model representing structured data extracted from a raw log.
 *
 * <p>
 * Every parser implementation converts an unstructured log into this model.
 * This object is never exposed outside the parser layer.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedLogData {

    /**
     * Log timestamp extracted from the raw log.
     */
    private LocalDateTime timestamp;

    /**
     * Parsed log level.
     */
    private LogLevel logLevel;

    /**
     * Main log message.
     */
    private String message;

    /**
     * Logger name.
     */
    private String loggerName;

    /**
     * Thread name.
     */
    private String threadName;

    /**
     * Source of the log.
     */
    private LogSource source;

    /**
     * Application name.
     */
    private String applicationName;

    /**
     * Service name.
     */
    private String serviceName;

    /**
     * Host name.
     */
    private String hostName;

    /**
     * Deployment environment.
     */
    private Environment environment;

    /**
     * Original raw log.
     */
    private String rawLog;

    /**
     * Parser-specific attributes.
     *
     * Examples:
     * Spring Boot -> PID, Package
     * Docker -> Container ID
     * Nginx -> HTTP Method, Status Code
     */
    private Map<String, String> metadata;

}