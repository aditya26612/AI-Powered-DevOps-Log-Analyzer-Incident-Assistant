package com.project.log_layer.kafka;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.project.log_layer.dto.request.ingest.ExternalLogEvent;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

record BrokerLogEnvelope(int version, UUID receiptId, String acceptedAt, UUID organizationId, long applicationId,
                         String applicationName, Environment environment, List<ExternalLogEvent> events) {
    static final int MAX_BYTES = 98_304;
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    static BrokerLogEnvelope create(Binding owner, List<ExternalLogEvent> validated) {
        return new BrokerLogEnvelope(1, UUID.randomUUID(), Instant.now().toString(), owner.organizationId(),
                owner.applicationId(), owner.applicationName(), owner.environment(), validated);
    }

    String partitionKey() {
        return organizationId + "/" + applicationId;
    }

    Binding owner() {
        return new Binding(organizationId, applicationId, applicationName, environment);
    }

    static BrokerLogEnvelope read(String key, byte[] value) {
        if (value == null || value.length == 0 || value.length > MAX_BYTES) {
            throw new PermanentRecordFailure("record_size", null);
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString();
            var envelope = JSON.readValue(json, BrokerLogEnvelope.class);
            if (envelope == null || envelope.version() != 1) {
                throw new PermanentRecordFailure("envelope_version", null);
            }
            if (envelope.receiptId() == null || envelope.acceptedAt() == null || envelope.organizationId() == null
                    || envelope.applicationId() <= 0 || envelope.applicationName() == null
                    || envelope.applicationName().isBlank() || envelope.applicationName().length() > 100
                    || envelope.environment() == null || envelope.events() == null
                    || envelope.events().isEmpty() || envelope.events().size() > ExternalBatchIngestion.MAX_EVENTS
                    || !envelope.partitionKey().equals(key)) {
                throw new PermanentRecordFailure("envelope_identity", null);
            }
            Instant.parse(envelope.acceptedAt());
            return envelope;
        } catch (IOException | DateTimeParseException e) {
            throw new PermanentRecordFailure("envelope_format", null);
        }
    }
}
