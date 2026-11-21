package com.project.log_layer.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ConfirmedPublisher implements AutoCloseable {
    private final KafkaProducer<String, byte[]> producer;
    private final Semaphore permits;
    private final int timeout;

    ConfirmedPublisher(KafkaMode mode, boolean recovery) {
        timeout = mode.getPublishTimeoutMs();
        permits = new Semaphore(recovery ? mode.getConcurrency() : 8);
        var config = new HashMap<>(mode.connection(recovery));
        config.put("client.id", recovery ? "devinsight-dlq" : "devinsight-ingress");
        config.put("acks", "all");
        config.put("enable.idempotence", true);
        config.put("retries", Integer.MAX_VALUE);
        config.put("max.in.flight.requests.per.connection", 5);
        config.put("max.block.ms", 1000);
        config.put("request.timeout.ms", Math.max(750, timeout / 2));
        config.put("delivery.timeout.ms", timeout);
        config.put("linger.ms", 5);
        config.put("buffer.memory", 2_097_152L);
        config.put("max.request.size", 131_072);
        this.producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer());
    }

    RecordMetadata send(String topic, String key, byte[] bytes) {
        if (!permits.tryAcquire()) {
            throw new PublicationUnavailable("publication_capacity");
        }
        try {
            return producer.send(new ProducerRecord<>(topic, key, bytes)).get(timeout + 500L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicationUnavailable("publication_interrupted");
        } catch (ExecutionException | TimeoutException | org.apache.kafka.common.KafkaException e) {
            throw new PublicationUnavailable("publication_unconfirmed");
        } finally {
            permits.release();
        }
    }

    @Override public void close() {
        producer.close(Duration.ofSeconds(2));
    }

    static final class PublicationUnavailable extends RuntimeException {
        PublicationUnavailable(String category) {
            super(category);
        }
    }
}
