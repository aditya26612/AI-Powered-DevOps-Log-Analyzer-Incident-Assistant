package com.project.log_layer.enums;

/**
 * Represents the supported fields that can be used
 * for sorting log records.
 *
 * This enum prevents invalid sort fields from being
 * passed by clients.
 */
public enum LogSortField {

    TIMESTAMP("timestamp"),
    LEVEL("level"),
    APPLICATION_NAME("applicationName"),
    SERVICE_NAME("serviceName"),
    STATUS("status"),
    CREATED_AT("createdAt");

    private final String field;

    LogSortField(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }
}