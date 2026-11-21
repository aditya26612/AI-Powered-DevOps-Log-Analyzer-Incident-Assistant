package com.project.log_layer.diagnosis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("diagnosis")
public record DiagnosisSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("2") int workers,
        @DefaultValue("250ms") Duration poll,
        @DefaultValue("30s") Duration lease,
        @DefaultValue("5s") Duration heartbeat,
        @DefaultValue("70s") Duration timeout,
        @DefaultValue("4") int attempts,
        @DefaultValue("2s") Duration initialDelay,
        @DefaultValue("60s") Duration maxDelay,
        @DefaultValue("20") int jitterPercent,
        @DefaultValue("8") int contextLogs) {

    public DiagnosisSettings {
        if (workers < 1 || workers > 4 || attempts < 1 || attempts > 6
                || poll.compareTo(Duration.ofMillis(50)) < 0 || poll.compareTo(Duration.ofSeconds(10)) > 0
                || lease.compareTo(Duration.ofSeconds(2)) < 0 || lease.compareTo(Duration.ofMinutes(5)) > 0
                || heartbeat.compareTo(Duration.ofMillis(250)) < 0 || heartbeat.multipliedBy(2).compareTo(lease) >= 0
                || timeout.compareTo(Duration.ofSeconds(1)) < 0 || timeout.compareTo(Duration.ofMinutes(5)) > 0
                || initialDelay.compareTo(Duration.ofMillis(100)) < 0 || maxDelay.compareTo(initialDelay) < 0
                || maxDelay.compareTo(Duration.ofMinutes(15)) > 0 || jitterPercent < 0 || jitterPercent > 50
                || contextLogs < 1 || contextLogs > 12) {
            throw new IllegalArgumentException("Diagnosis worker configuration outside supported bounds");
        }
    }

    long retryDelay(int attempt, double noise) {
        long max = maxDelay.toMillis();
        long delay = Math.min(max, initialDelay.toMillis() * (1L << Math.min(attempt - 1, 12)));
        return Math.max(1, Math.min(max, Math.round(delay * (1 + noise * jitterPercent / 100))));
    }
}
