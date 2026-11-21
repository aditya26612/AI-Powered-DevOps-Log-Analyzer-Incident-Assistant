package com.project.log_layer.kafka;

import com.project.log_layer.service.ExternalBatchIngestion;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class KafkaWorker implements SmartLifecycle {
    private final KafkaMode mode;
    private final KafkaRecordProcessor processor;
    private final MeterRegistry registry;
    private final ConfirmedPublisher recovery;
    private final List<KafkaConsumer<String, byte[]>> consumers = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile long lastAssignedPoll;
    private ExecutorService executor;

    KafkaWorker(KafkaMode mode, KafkaRecordProcessor processor, MeterRegistry registry) {
        this.mode = mode;
        this.processor = processor;
        this.registry = registry;
        recovery = new ConfirmedPublisher(mode, true);
    }

    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }

    boolean hasPolled() {
        return lastAssignedPoll != 0 && System.nanoTime() - lastAssignedPoll < Duration.ofMinutes(3).toNanos();
    }

    @Override public synchronized void start() {
        if (running) return;
        running = true;
        executor = Executors.newFixedThreadPool(mode.getConcurrency(),
                Thread.ofPlatform().daemon().name("devinsight-kafka-worker-", 0).factory());
        for (int i = 0; i < mode.getConcurrency(); i++) {
            int worker = i;
            executor.execute(() -> run(worker));
        }
    }

    @Override public synchronized void stop() {
        running = false;
        consumers.forEach(KafkaConsumer::wakeup);
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(8, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @jakarta.annotation.PreDestroy void closePublisher() {
        recovery.close();
    }

    static Map<String, Object> consumerProperties(KafkaMode mode, String clientId) {
        var config = new HashMap<>(mode.connection(true));
        config.put("client.id", clientId);
        config.put("group.id", mode.getGroup());
        config.put("enable.auto.commit", false);
        config.put("auto.offset.reset", "earliest");
        config.put("allow.auto.create.topics", false);
        config.put("max.poll.records", 1);
        config.put("max.poll.interval.ms", 300_000);
        config.put("fetch.max.bytes", 524_288);
        config.put("max.partition.fetch.bytes", 131_072);
        config.put("isolation.level", "read_committed");
        return config;
    }

    private void run(int number) {
        try (var consumer = new KafkaConsumer<>(consumerProperties(mode, "devinsight-worker-" + number),
                new StringDeserializer(), new ByteArrayDeserializer())) {
            consumers.add(consumer);
            try {
                poll(consumer);
            } finally {
                consumers.remove(consumer);
            }
        } catch (WakeupException e) {
            if (running) count("unexpected_wakeup");
        } catch (RuntimeException e) {
            count("worker_stopped");
        }
    }

    private void poll(KafkaConsumer<String, byte[]> consumer) {
        var pausedUntil = new HashMap<TopicPartition, Long>();
        consumer.subscribe(List.of(mode.getRawTopic()), new ConsumerRebalanceListener() {
            @Override public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                partitions.forEach(pausedUntil::remove);
                count("revoked");
            }

            @Override public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                count("assigned");
            }
        });
        while (running) {
            try {
                resumeReady(consumer, pausedUntil);
                var records = consumer.poll(Duration.ofMillis(500));
                if (!consumer.assignment().isEmpty()) lastAssignedPoll = System.nanoTime();
                for (var record : records) {
                    if (!running) return;
                    handle(consumer, record, pausedUntil);
                }
            } catch (WakeupException stopping) {
                if (!running) return;
                throw stopping;
            } catch (RuntimeException brokerFailure) {
                count("poll_failure");
                if (!delay(1000)) return;
            }
        }
    }

    private void handle(KafkaConsumer<String, byte[]> consumer, ConsumerRecord<String, byte[]> record,
                        Map<TopicPartition, Long> pausedUntil) {
        var partition = new TopicPartition(record.topic(), record.partition());
        for (int attempt = 1; attempt <= mode.getAttempts(); attempt++) {
            try {
                var result = processor.process(record);
                var duplicates = result.events().stream()
                        .filter(event -> event.status() == ExternalBatchIngestion.Status.DUPLICATE).count();
                registry.counter("devinsight.kafka.consumed.events", "result", "duplicate").increment(duplicates);
                registry.counter("devinsight.kafka.consumed.events", "result", "persisted")
                        .increment(result.events().size() - duplicates);
            } catch (PermanentRecordFailure poison) {
                if (running && deadLetter(record, poison, attempt)) {
                    commitOrDefer(consumer, record, partition, pausedUntil);
                } else if (running) {
                    defer(consumer, record, partition, pausedUntil);
                }
                return;
            } catch (RuntimeException transientFailure) {
                count("processing_retry");
                if (attempt < mode.getAttempts()) {
                    if (!delay(250L << (attempt - 1))) return;
                    continue;
                }
                if (running) defer(consumer, record, partition, pausedUntil);
                return;
            }
            if (running) commitOrDefer(consumer, record, partition, pausedUntil);
            return;
        }
    }

    private boolean deadLetter(ConsumerRecord<String, byte[]> record, PermanentRecordFailure failure, int attempt) {
        try {
            var envelope = failure.envelope();
            var receipt = BrokerLogEnvelope.JSON.createObjectNode();
            receipt.put("version", 1).put("topic", record.topic()).put("partition", record.partition())
                    .put("offset", record.offset()).put("category", failure.getMessage()).put("attempts", attempt)
                    .put("recordedAt", Instant.now().toString());
            if (envelope != null) {
                receipt.put("receiptId", envelope.receiptId().toString())
                        .put("organizationId", envelope.organizationId().toString())
                        .put("applicationId", Long.toString(envelope.applicationId()));
                var ids = receipt.putArray("eventIds");
                envelope.events().stream().filter(e -> e != null && e.eventId() != null)
                        .forEach(e -> ids.add(e.eventId().toString()));
            }
            String key = record.topic() + "/" + record.partition() + "/" + record.offset();
            recovery.send(mode.getDeadLetterTopic(), key, BrokerLogEnvelope.JSON.writeValueAsBytes(receipt));
            count("dlq_confirmed");
            return true;
        } catch (Exception publicationFailure) {
            count("dlq_unconfirmed");
            return false;
        }
    }

    private void commitOrDefer(KafkaConsumer<String, byte[]> consumer, ConsumerRecord<String, byte[]> record,
                               TopicPartition partition, Map<TopicPartition, Long> pausedUntil) {
        try {
            commit(consumer, record);
            count("offset_committed");
        } catch (WakeupException e) {
            throw e;
        } catch (RuntimeException failedCommit) {
            count("offset_unconfirmed");
            if (running) defer(consumer, record, partition, pausedUntil);
        }
    }

    static void commit(KafkaConsumer<String, byte[]> consumer, ConsumerRecord<String, byte[]> record) {
        var partition = new TopicPartition(record.topic(), record.partition());
        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)), Duration.ofSeconds(3));
    }

    private void defer(KafkaConsumer<String, byte[]> consumer, ConsumerRecord<String, byte[]> record,
                       TopicPartition partition, Map<TopicPartition, Long> pausedUntil) {
        if (!consumer.assignment().contains(partition)) return;
        consumer.seek(partition, record.offset());
        consumer.pause(Set.of(partition));
        pausedUntil.put(partition, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(mode.getPauseMs()));
        count("partition_deferred");
    }

    private static void resumeReady(KafkaConsumer<String, byte[]> consumer, Map<TopicPartition, Long> deadlines) {
        var ready = new ArrayList<TopicPartition>();
        long now = System.nanoTime();
        deadlines.forEach((partition, until) -> {
            if (until <= now && consumer.assignment().contains(partition)) ready.add(partition);
        });
        if (!ready.isEmpty()) {
            consumer.resume(ready);
            ready.forEach(deadlines::remove);
        }
    }

    private boolean delay(long millis) {
        try {
            Thread.sleep(millis);
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void count(String outcome) {
        registry.counter("devinsight.kafka.worker.operations", "result", outcome).increment();
    }
}
