package com.project.log_layer.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class KafkaLagMonitor {
    private static final long MAX_SAMPLE_AGE = Duration.ofSeconds(10).toNanos();
    private final KafkaMode mode;
    private final Admin admin;
    private final java.util.concurrent.ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("devinsight-kafka-inspect").factory());
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile Snapshot last = new Snapshot(-1, 0, 0, false);
    private record Snapshot(long lag, long sampledAt, long previousAttempts, boolean available) {}

    KafkaLagMonitor(KafkaMode mode, MeterRegistry registry) {
        this.mode = mode;
        var properties = new HashMap<>(mode.connection(!mode.isProducerEnabled()));
        properties.put("client.id", "devinsight-kafka-readiness");
        properties.put("request.timeout.ms", 1500);
        properties.put("default.api.timeout.ms", 2500);
        admin = Admin.create(properties);
        registry.gauge("devinsight.kafka.backlog.records", this, monitor -> monitor.last.lag());
    }

    @PostConstruct void start() {
        worker.scheduleWithFixedDelay(this::refresh, 0, 1, TimeUnit.SECONDS);
    }

    private void refresh() {
        long previousAttempts = attempts.get();
        try {
            var topics = mode.isConsumerEnabled() && !mode.isProducerEnabled()
                    ? Set.of(mode.getRawTopic(), mode.getDeadLetterTopic()) : Set.of(mode.getRawTopic());
            var metadata = admin.describeTopics(topics).allTopicNames().get(2, TimeUnit.SECONDS);
            if (metadata.values().stream().anyMatch(topic -> topic.partitions().isEmpty()
                    || topic.partitions().stream().anyMatch(partition -> partition.leader() == null
                    || partition.leader().equals(Node.noNode())))) {
                last = new Snapshot(-1, 0, 0, false);
                return;
            }

            String raw = mode.getRawTopic();
            var earliestRequest = new HashMap<TopicPartition, OffsetSpec>();
            var latestRequest = new HashMap<TopicPartition, OffsetSpec>();
            metadata.get(raw).partitions().forEach(item -> {
                var tp = new TopicPartition(raw, item.partition());
                earliestRequest.put(tp, OffsetSpec.earliest());
                latestRequest.put(tp, OffsetSpec.latest());
            });
            var earliestFuture = admin.listOffsets(earliestRequest).all();
            var latestFuture = admin.listOffsets(latestRequest).all();
            var committedFuture = admin.listConsumerGroupOffsets(mode.getGroup()).partitionsToOffsetAndMetadata();
            var earliest = earliestFuture.get(2, TimeUnit.SECONDS);
            var latest = latestFuture.get(2, TimeUnit.SECONDS);
            var committed = committedFuture.get(2, TimeUnit.SECONDS);
            long lag = 0;
            for (var entry : latest.entrySet()) {
                long first = earliest.get(entry.getKey()).offset();
                var cursor = committed.get(entry.getKey());
                long consumed = cursor == null ? first : Math.max(first, cursor.offset());
                lag += Math.max(0, entry.getValue().offset() - consumed);
            }
            last = new Snapshot(lag, System.nanoTime(), previousAttempts, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            last = new Snapshot(-1, 0, 0, false);
        }
    }

    boolean healthy() {
        Snapshot snapshot = last;
        return snapshot.available() && System.nanoTime() - snapshot.sampledAt() < MAX_SAMPLE_AGE;
    }

    synchronized boolean reserve() {
        Snapshot snapshot = last;
        if (!snapshot.available() || System.nanoTime() - snapshot.sampledAt() >= MAX_SAMPLE_AGE
                || snapshot.lag() + attempts.get() - snapshot.previousAttempts() + pending.get() >= mode.getMaxBacklog()) {
            return false;
        }
        attempts.incrementAndGet();
        pending.incrementAndGet();
        return true;
    }

    void release() {
        pending.decrementAndGet();
    }

    @PreDestroy void stop() {
        worker.shutdownNow();
        admin.close(Duration.ofSeconds(1));
    }
}
