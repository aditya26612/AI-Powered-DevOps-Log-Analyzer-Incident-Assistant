package com.project.log_layer.util;

import java.time.format.DateTimeFormatter;

/**
 * Centralized DateTimeFormatters used by log parsers.
 */
public final class DateTimeFormats {

    private DateTimeFormats() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Spring Boot timestamp.
     *
     * Example:
     * 2026-06-26T20:15:31.123+05:30
     */
    public static final DateTimeFormatter SPRING_BOOT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Docker timestamp.
     *
     * Example:
     * 2026-06-26T20:15:31.123456789Z
     */
    public static final DateTimeFormatter DOCKER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Kubernetes timestamp.
     */
    public static final DateTimeFormatter KUBERNETES =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Nginx timestamp.
     *
     * Example:
     * 26/Jun/2026:20:15:31 +0530
     */
    public static final DateTimeFormatter NGINX =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

}