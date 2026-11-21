package com.project.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded batch delivery for Logback. Keep the application's normal appenders configured separately. */
public class DevInsightLogbackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private String endpoint;
    private String apiKey;
    private String serviceName;
    private String environment;
    private volatile BatchCollector collector;

    @Override
    public void start() {
        if (isStarted()) return;
        try {
            String mode = System.getenv().getOrDefault("DEVINSIGHT_DELIVERY_MODE", "SYNC_PERSISTED");
            boolean asynchronous = switch (mode) {
                case "ASYNC_KAFKA" -> true;
                case "SYNC_PERSISTED" -> false;
                default -> throw new IllegalArgumentException("Invalid DevInsight delivery mode");
            };
            String path = asynchronous ? BatchCollector.ASYNC_PATH : BatchCollector.PATH;
            collector = new BatchCollector(
                    fallback(endpoint, "DEVINSIGHT_ENDPOINT", "http://127.0.0.1:8080" + path),
                    fallback(apiKey, "DEVINSIGHT_API_KEY", null),
                    fallback(serviceName, "DEVINSIGHT_APPLICATION_NAME", null),
                    fallback(environment, "DEVINSIGHT_ENVIRONMENT", null),
                    BatchCollector.Settings.fromEnvironment(), asynchronous);
            super.start();
        } catch (IllegalArgumentException invalid) {
            addError("DevInsight collector configuration is invalid. Check the endpoint, credential and application settings.");
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        BatchCollector sender = collector;
        if (sender != null) {
            var throwable = event.getThrowableProxy();
            var mdc = event.getMDCPropertyMap();
            String parent = mdc.get("traceparent");
            if (parent == null && mdc.containsKey("trace_id") && mdc.containsKey("span_id")
                    && mdc.containsKey("trace_flags")) {
                parent = "00-" + mdc.get("trace_id") + "-" + mdc.get("span_id") + "-" + mdc.get("trace_flags");
            }
            sender.capture(event.getLevel().toString(), Instant.ofEpochMilli(event.getTimeStamp()),
                    event.getFormattedMessage(), throwable == null ? null : ThrowableProxyUtil.asString(throwable),
                    event.getLoggerName(), event.getThreadName(), parent, mdc.get("trace_origin"));
        }
    }

    @Override
    public void stop() {
        super.stop();
        BatchCollector sender = collector;
        if (sender != null) sender.close();
    }

    public Map<String, Object> stats() {
        if (collector == null) return Map.of();
        var result = new LinkedHashMap<String, Object>();
        var stats = collector.stats();
        result.put("captured", stats.captured());
        result.put("enqueued", stats.enqueued());
        result.put("requests", stats.requests());
        result.put("attemptedEvents", stats.attemptedEvents());
        result.put("acknowledged", stats.acknowledged());
        result.put("duplicates", stats.duplicates());
        result.put("brokerAccepted", stats.brokerAccepted());
        result.put("persisted", stats.persisted());
        result.put("retries", stats.retries());
        result.put("failedBatches", stats.failedBatches());
        result.put("rejected", stats.rejected());
        result.put("dropped", stats.dropped());
        result.put("queueDepth", stats.queueDepth());
        result.put("peakDepth", stats.peakDepth());
        result.put("inFlight", stats.inFlight());
        result.put("lastSuccess", stats.lastSuccess());
        result.put("lastFailure", stats.lastFailure());
        return result;
    }

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setDevInsightUrl(String endpoint) { this.endpoint = endpoint; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setEnvironment(String environment) { this.environment = environment; }

    private static String fallback(String value, String variable, String defaultValue) {
        return value == null || value.isBlank() ? System.getenv().getOrDefault(variable, defaultValue) : value;
    }
}
