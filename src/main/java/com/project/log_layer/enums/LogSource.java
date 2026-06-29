package com.project.log_layer.enums;

/**
 * Represents the source from which a log entry originates.
 * Used by the parser layer to identify the appropriate parser
 * and by the dashboard for log source analytics.
 */
public enum LogSource {

    SPRING_BOOT,
    DOCKER,
    NGINX,
    KUBERNETES,
    APACHE,
    JENKINS,
    KAFKA,
    REDIS,
    MYSQL,
    SYSTEM,
    CUSTOM

}