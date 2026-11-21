package com.project.log_layer.kafka;

import com.project.log_layer.service.ExternalBatchIngestion;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.web.server.ResponseStatusException;

final class KafkaRecordProcessor {
    private final ExternalBatchIngestion batches;
    private final KafkaMode mode;

    KafkaRecordProcessor(ExternalBatchIngestion batches, KafkaMode mode) {
        this.batches = batches;
        this.mode = mode;
    }

    ExternalBatchIngestion.Result process(ConsumerRecord<String, byte[]> record) {
        if (!mode.getRawTopic().equals(record.topic())) {
            throw new PermanentRecordFailure("unexpected_topic", null);
        }
        var envelope = BrokerLogEnvelope.read(record.key(), record.value());
        try {
            return batches.consume(envelope.owner(), envelope.events());
        } catch (ResponseStatusException e) {
            switch (e.getStatusCode().value()) {
                case 400, 413 -> throw new PermanentRecordFailure("invalid_event", envelope);
                case 403 -> throw new PermanentRecordFailure("unrecognized_application", envelope);
                case 409 -> throw new PermanentRecordFailure("event_conflict", envelope);
                default -> throw e;
            }
        }
    }
}
