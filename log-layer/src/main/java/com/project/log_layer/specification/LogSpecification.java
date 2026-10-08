package com.project.log_layer.specification;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.enums.LogStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contains reusable JPA Specifications for filtering logs.
 *
 * <p>
 * Each method builds a Specification for a single searchable field.
 * Specifications are composed later by LogSpecificationBuilder.
 * </p>
 */
public final class LogSpecification {

    private LogSpecification() {
        throw new IllegalStateException("Utility class");
    }

    /*
     * ---------------------------------------------------------
     * Equality Filters
     * ---------------------------------------------------------
     */

    public static Specification<Log> hasLevel(LogLevel level) {

        if (level == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("level"), level);
    }

    public static Specification<Log> hasSource(LogSource source) {

        if (source == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("source"), source);
    }

    public static Specification<Log> hasEnvironment(Environment environment) {

        if (environment == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("environment"), environment);
    }

    public static Specification<Log> hasStatus(LogStatus status) {

        if (status == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<Log> hasAnomaly(Boolean anomaly) {

        if (anomaly == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("anomaly"), anomaly);
    }

    public static Specification<Log> hasCorrelationId(UUID correlationId) {

        if (correlationId == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    /*
     * ---------------------------------------------------------
     * String Filters (Case Insensitive)
     * ---------------------------------------------------------
     */

    public static Specification<Log> hasApplicationName(String applicationName) {

        if (applicationName == null || applicationName.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("applicationName")),
                        "%" + applicationName.toLowerCase() + "%"
                );
    }

    public static Specification<Log> hasServiceName(String serviceName) {

        if (serviceName == null || serviceName.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("serviceName")),
                        "%" + serviceName.toLowerCase() + "%"
                );
    }

    public static Specification<Log> hasLoggerName(String loggerName) {

        if (loggerName == null || loggerName.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("loggerName")),
                        "%" + loggerName.toLowerCase() + "%"
                );
    }

    public static Specification<Log> hasThreadName(String threadName) {

        if (threadName == null || threadName.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("threadName")),
                        "%" + threadName.toLowerCase() + "%"
                );
    }

    public static Specification<Log> hasHostName(String hostName) {

        if (hostName == null || hostName.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("hostName")),
                        "%" + hostName.toLowerCase() + "%"
                );
    }

    public static Specification<Log> hasMessage(String message) {

        if (message == null || message.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("message")),
                        "%" + message.toLowerCase() + "%"
                );
    }

    /*
     * ---------------------------------------------------------
     * Time Range Filter
     * ---------------------------------------------------------
     */

    public static Specification<Log> timestampBetween(
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (startTime == null && endTime == null) {
            return null;
        }

        return (root, query, cb) -> {

            if (startTime != null && endTime != null) {
                return cb.between(
                        root.get("timestamp"),
                        startTime,
                        endTime
                );
            }

            if (startTime != null) {
                return cb.greaterThanOrEqualTo(
                        root.get("timestamp"),
                        startTime
                );
            }

            return cb.lessThanOrEqualTo(
                    root.get("timestamp"),
                    endTime
            );
        };
    }

}