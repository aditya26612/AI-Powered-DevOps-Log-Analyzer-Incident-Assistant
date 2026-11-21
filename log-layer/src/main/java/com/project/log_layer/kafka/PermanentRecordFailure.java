package com.project.log_layer.kafka;

final class PermanentRecordFailure extends RuntimeException {
    private final BrokerLogEnvelope envelope;

    PermanentRecordFailure(String category, BrokerLogEnvelope envelope) {
        super(category);
        this.envelope = envelope;
    }

    BrokerLogEnvelope envelope() {
        return envelope;
    }
}
