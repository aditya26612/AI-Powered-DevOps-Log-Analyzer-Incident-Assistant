package com.project.log_layer.enums;

/**
 * Represents the supported fields that can be used
 * for sorting log records.
 *
 * This enum prevents invalid sort fields from being
 * passed by clients.
 */
public enum LogSortField {

    /**
     * Sort by log timestamp.
     */
    TIMESTAMP,

    /**
     * Sort by log severity.
     */
    LEVEL,

    /**
     * Sort by service name.
     */
    SERVICE_NAME,

    /**
     * Sort by application name.
     */
    APPLICATION_NAME,

    /**
     * Sort by anomaly score.
     */
    ANOMALY_SCORE,

    /**
     * Sort by database creation time.
     */
    CREATED_AT

}