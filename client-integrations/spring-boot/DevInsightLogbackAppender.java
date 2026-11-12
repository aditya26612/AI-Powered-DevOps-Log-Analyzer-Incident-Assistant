package com.project.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drop-in Logback Appender that streams logs directly into DevInsight AI Incident Assistant.
 *
 * <p>Usage: Add this class to your Spring Boot project and reference it in your
 * {@code logback-spring.xml}.</p>
 */
public class DevInsightLogbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.systemDefault());

    private String devInsightUrl = "http://localhost:8080/api/v1/logs";
    private String apiKey = "devinsight_internal_ml_key_secret_12345";
    private String userEmail = "devops@company.com";
    private String serviceName = "spring-boot-service";
    private String environment = "PRODUCTION";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "devinsight-log-shipper");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null) {
            return;
        }

        // Only ship WARN, ERROR, and FATAL logs to DevInsight
        if (eventObject.getLevel().toInt() < ch.qos.logback.classic.Level.WARN_INT) {
            return;
        }

        executor.submit(() -> shipLog(eventObject));
    }

    private void shipLog(ILoggingEvent event) {
        try {
            String timestamp = ISO_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp()));
            String level = event.getLevel().toString();
            String thread = event.getThreadName();
            String logger = event.getLoggerName();
            String message = event.getFormattedMessage();

            StringBuilder fullLog = new StringBuilder();
            fullLog.append(timestamp).append(" ")
                    .append(String.format("%-5s", level)).append(" 1 --- [")
                    .append(thread).append("] ")
                    .append(logger).append(" : ")
                    .append(message);

            IThrowableProxy throwable = event.getThrowableProxy();
            if (throwable != null) {
                fullLog.append("\n").append(throwable.getClassName()).append(": ").append(throwable.getMessage());
                for (StackTraceElementProxy step : throwable.getStackTraceElementProxyArray()) {
                    fullLog.append("\n\tat ").append(step.getSTEAsString());
                }
            }

            String payload = buildJsonPayload(fullLog.toString(), serviceName, environment);

            URL url = URI.create(devInsightUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                conn.setRequestProperty("X-API-Key", apiKey);
            }
            if (userEmail != null && !userEmail.isBlank()) {
                conn.setRequestProperty("X-User-Email", userEmail);
            }
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();
            if (statusCode >= 400) {
                addWarn("DevInsight returned HTTP " + statusCode + " for log event");
            }

        } catch (Exception ex) {
            addWarn("Failed to ship log to DevInsight: " + ex.getMessage());
        }
    }

    private String buildJsonPayload(String rawLog, String appName, String env) {
        return "{\n" +
                "  \"rawLog\": " + escapeJson(rawLog) + ",\n" +
                "  \"source\": \"SPRING_BOOT\",\n" +
                "  \"environment\": \"" + env + "\",\n" +
                "  \"applicationName\": \"" + appName + "\"\n" +
                "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public void setDevInsightUrl(String devInsightUrl) {
        this.devInsightUrl = devInsightUrl;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}

