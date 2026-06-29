package com.project.log_layer.util;

import java.util.regex.Pattern;

/**
 * Centralized regular expression patterns used by log parsers.
 *
 * <p>
 * All patterns are precompiled for better performance and use
 * named capturing groups for improved readability.
 * </p>
 */
public final class RegexPatterns {

    private RegexPatterns() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Spring Boot (Logback) log pattern.
     *
     * Example:
     * 2026-06-26T20:15:31.123+05:30 INFO 12345 --- [main]
     * com.project.service.UserService : User created successfully
     */
    public static final Pattern SPRING_BOOT_PATTERN =
            Pattern.compile(
                    "^(?<timestamp>\\S+)\\s+" +
                            "(?<level>TRACE|DEBUG|INFO|WARN|ERROR)\\s+" +
                            "(?<pid>\\d+)\\s+---\\s+" +
                            "\\[(?<thread>[^\\]]+)]\\s+" +
                            "(?<logger>[\\w.$]+)\\s*:\\s*" +
                            "(?<message>.*)$"
            );

    /**
     * Docker log pattern.
     *
     * Example:
     * 2026-06-26T20:15:31.123456789Z Started container
     */
    public static final Pattern DOCKER_PATTERN =
            Pattern.compile(
                    "^(?<timestamp>\\S+)\\s+" +
                            "(?<message>.*)$"
            );

    /**
     * Kubernetes log pattern.
     *
     * Example:
     * 2026-06-26T20:15:31.123456789Z stdout F Started service
     */
    public static final Pattern KUBERNETES_PATTERN =
            Pattern.compile(
                    "^(?<timestamp>\\S+)\\s+" +
                            "(?<stream>stdout|stderr)\\s+" +
                            "(?<flag>\\S+)\\s+" +
                            "(?<message>.*)$"
            );

    /**
     * Nginx access log pattern.
     */
    public static final Pattern NGINX_PATTERN =
            Pattern.compile(
                    "^(?<clientIp>\\S+)\\s+-\\s+-\\s+" +
                            "\\[(?<timestamp>[^]]+)]\\s+" +
                            "\"(?<method>\\S+)\\s+" +
                            "(?<url>\\S+)\\s+" +
                            "(?<protocol>[^\"]+)\"\\s+" +
                            "(?<status>\\d{3})\\s+" +
                            "(?<bytes>\\d+)\\s+" +
                            "\"(?<referrer>[^\"]*)\"\\s+" +
                            "\"(?<userAgent>[^\"]*)\"$"
            );

}