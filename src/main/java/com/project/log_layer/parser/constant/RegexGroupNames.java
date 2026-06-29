package com.project.log_layer.parser.constant;

/**
 * Named regex groups used by all parser implementations.
 */
public final class RegexGroupNames {

    private RegexGroupNames() {
        throw new IllegalStateException("Utility class");
    }

    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String THREAD = "thread";
    public static final String LOGGER = "logger";
    public static final String MESSAGE = "message";

    // Spring Boot
    public static final String PID = "pid";

    // Docker / Kubernetes
    public static final String STREAM = "stream";
    public static final String FLAG = "flag";

    // Nginx
    public static final String CLIENT_IP = "clientIp";
    public static final String METHOD = "method";
    public static final String URL = "url";
    public static final String PROTOCOL = "protocol";
    public static final String STATUS = "status";
    public static final String BYTES = "bytes";
    public static final String REFERRER = "referrer";
    public static final String USER_AGENT = "userAgent";
}