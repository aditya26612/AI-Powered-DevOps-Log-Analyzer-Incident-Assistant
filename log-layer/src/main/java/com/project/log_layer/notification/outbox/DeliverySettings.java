package com.project.log_layer.notification.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("notification.delivery")
public record DeliverySettings(
        @DefaultValue("OFF") Transport transport,
        @DefaultValue("true") boolean worker,
        @DefaultValue("2") int workers,
        @DefaultValue("500ms") Duration poll,
        @DefaultValue("30s") Duration lease,
        @DefaultValue("5s") Duration heartbeat,
        @DefaultValue("8s") Duration httpTimeout,
        @DefaultValue("24h") Duration lifetime,
        @DefaultValue("5") int attempts,
        String fixtureUrl) {

    public enum Transport { OFF, SLACK, FIXTURE }

    public DeliverySettings {
        if (workers < 1 || workers > 4 || attempts < 1 || attempts > 5
                || poll.compareTo(Duration.ofMillis(100)) < 0 || poll.compareTo(Duration.ofSeconds(10)) > 0
                || heartbeat.compareTo(Duration.ofMillis(250)) < 0 || heartbeat.multipliedBy(2).compareTo(lease) >= 0
                || httpTimeout.compareTo(Duration.ofMillis(500)) < 0 || httpTimeout.compareTo(lease) >= 0
                || lease.compareTo(Duration.ofMinutes(2)) > 0 || lifetime.compareTo(lease) <= 0
                || lifetime.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Notification delivery settings outside supported bounds");
        }
    }
}
