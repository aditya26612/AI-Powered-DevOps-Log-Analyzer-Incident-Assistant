package com.project.log_layer.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

public final class KafkaIngress implements AutoCloseable {
    private final KafkaMode mode;
    private final ExternalBatchIngestion batches;
    private final KafkaLagMonitor monitor;
    private final MeterRegistry registry;
    private final ConfirmedPublisher publisher;

    KafkaIngress(KafkaMode mode, ExternalBatchIngestion batches, KafkaLagMonitor monitor, MeterRegistry registry) {
        this.mode = mode;
        this.batches = batches;
        this.monitor = monitor;
        this.registry = registry;
        this.publisher = mode.isProducerEnabled() ? new ConfirmedPublisher(mode, false) : null;
    }

    public record Accepted(UUID receiptId, String acknowledgement, String processingState, List<Event> events) {}
    public record Event(UUID eventId, String status) {}

    public Accepted accept(byte[] body, Binding owner) {
        var events = batches.validateForApplication(body, owner);
        if (publisher == null || !monitor.healthy()) {
            registry.counter("devinsight.kafka.publications", "result", "not_ready").increment();
            throw unavailable();
        }
        var envelope = BrokerLogEnvelope.create(owner, events);
        byte[] record;
        try {
            record = BrokerLogEnvelope.JSON.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode the validated batch");
        }
        if (record.length > BrokerLogEnvelope.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Kafka envelope exceeds its byte limit");
        }
        if (!monitor.reserve()) {
            registry.counter("devinsight.kafka.publications", "result", "backlog_limited").increment();
            throw unavailable();
        }
        try {
            batches.charge(owner, events.size());
            publisher.send(mode.getRawTopic(), envelope.partitionKey(), record);
        } catch (ConfirmedPublisher.PublicationUnavailable e) {
            registry.counter("devinsight.kafka.publications", "result", e.getMessage()).increment();
            throw unavailable();
        } finally {
            monitor.release();
        }
        registry.counter("devinsight.kafka.publications", "result", "confirmed").increment();
        registry.counter("devinsight.kafka.broker.accepted.events").increment(events.size());
        return new Accepted(envelope.receiptId(), "BROKER_ACCEPTED", "ASYNCHRONOUS",
                events.stream().map(event -> new Event(event.eventId(), "BROKER_ACCEPTED")).toList());
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Broker acceptance was not confirmed. Retry with the original event IDs");
    }

    @Override public void close() {
        if (publisher != null) publisher.close();
    }
}
