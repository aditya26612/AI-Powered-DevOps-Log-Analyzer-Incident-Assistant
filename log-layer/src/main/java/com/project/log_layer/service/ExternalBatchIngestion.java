package com.project.log_layer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.project.log_layer.dto.request.ingest.ExternalLogEvent;
import com.project.log_layer.dto.request.ingest.TraceContext;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.entity.Log;
import com.project.log_layer.security.IngestionAdmission;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import com.project.log_layer.tenant.TenantSqlScope;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Service
public class ExternalBatchIngestion {
    public static final int MAX_EVENTS = 10;
    private static final int MAX_TEXT_BYTES = 16_384;
    private static final Pattern METADATA_KEY = Pattern.compile("[a-zA-Z0-9_.-]{1,64}");
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            ".*(?:password|passwd|secret|token|cookie|authorization|api[-_.]?key|connection[-_.]?string).*",
            Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();
    private static final ObjectReader READER = JSON.readerFor(Request.class);
    private final Semaphore simultaneous = new Semaphore(2);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TenantSqlScope scope;
    private final LogService logs;
    private final IngestionAdmission admission;

    public ExternalBatchIngestion(JdbcTemplate jdbc, PlatformTransactionManager manager, TenantSqlScope scope,
                                  LogService logs, IngestionAdmission admission) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.scope = scope;
        this.logs = logs;
        this.admission = admission;
    }

    public record Request(String applicationName, Environment environment, List<ExternalLogEvent> events) {}
    public record Result(List<Acknowledgement> events) {}
    public record Acknowledgement(UUID eventId, String logId, Status status) {}
    public enum Status { ACCEPTED, DUPLICATE }
    private record Existing(long logId, byte[] digest) {}

    public Result submit(byte[] body, Binding owner) {
        List<ExternalLogEvent> events = validateForApplication(body, owner);
        if (!simultaneous.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Batch ingestion is busy");
        }
        try {
            charge(owner, events.size());
            return transaction.execute(status -> persist(events, owner, null));
        } finally {
            simultaneous.release();
        }
    }

    public List<ExternalLogEvent> validateForApplication(byte[] body, Binding owner) {
        Request request = parse(body);
        if (!owner.applicationName().equals(request.applicationName()) || owner.environment() != request.environment()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Application metadata does not match the credential");
        }
        return validate(request);
    }

    public void charge(Binding owner, int size) {
        if (!admission.allowApplication(owner.applicationId(), size)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Application event budget exceeded");
        }
    }

    public Result consume(Binding owner, List<ExternalLogEvent> input) {
        var events = validate(new Request(owner.applicationName(), owner.environment(), input));
        var known = transaction.execute(status -> {
            scope.enter(owner.organizationId());
            requireApplication(owner);
            return inspect(events, owner);
        });
        var prepared = new HashMap<UUID, Log>();
        for (int i = 0; i < events.size(); i++) {
            if (known.get(i) == null) {
                var event = events.get(i);
                prepared.put(event.eventId(), logs.prepareExternal(event, owner, digest(event)));
            }
        }
        return transaction.execute(status -> {
            // The identity and conflict check runs again under the shared Stage 03 writer lock.
            requireApplication(owner);
            return persist(events, owner, prepared);
        });
    }

    private void requireApplication(Binding owner) {
        Boolean recognized = jdbc.queryForObject("""
                select exists(select 1 from public.application_integrations
                              where organization_id=? and id=?)
                """, Boolean.class, owner.organizationId(), owner.applicationId());
        if (!Boolean.TRUE.equals(recognized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown application association");
        }
    }

    private Result persist(List<ExternalLogEvent> events, Binding owner, Map<UUID, Log> prepared) {
        scope.enter(owner.organizationId());
        var lock = jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                Boolean.class, "devinsight.external.batch:" + owner.applicationId());
        if (!Boolean.TRUE.equals(lock)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Application already has a batch in progress");
        }

        var existing = inspect(events, owner);
        var acknowledgements = new ArrayList<Acknowledgement>(events.size());
        for (int i = 0; i < events.size(); i++) {
            var event = events.get(i);
            var row = existing.get(i);
            if (row != null) {
                acknowledgements.add(new Acknowledgement(event.eventId(), Long.toString(row.logId()), Status.DUPLICATE));
                continue;
            }
            long id = prepared == null ? logs.ingestExternal(event, owner, digest(event))
                    : logs.storePreparedExternal(prepared.get(event.eventId()));
            acknowledgements.add(new Acknowledgement(event.eventId(), Long.toString(id), Status.ACCEPTED));
        }
        return new Result(acknowledgements);
    }

    private List<Existing> inspect(List<ExternalLogEvent> events, Binding owner) {
        var existing = new ArrayList<Existing>(events.size());
        for (ExternalLogEvent event : events) {
            var row = jdbc.query("""
                    select id, external_event_digest from public.logs
                    where organization_id = ? and application_id = ? and external_event_id = ?
                    """, (rs, i) -> new Existing(rs.getLong(1), rs.getBytes(2)),
                    owner.organizationId(), owner.applicationId(), event.eventId()).stream().findFirst().orElse(null);
            if (row != null && !Arrays.equals(row.digest(), digest(event))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An event ID was previously used with different content");
            }
            existing.add(row);
        }
        return existing;
    }

    private static Request parse(byte[] body) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            Request request = READER.readValue(json);
            if (request != null) {
                return request;
            }
        } catch (CharacterCodingException | JsonProcessingException ignored) {
            // Return a fixed diagnostic with no customer input.
        }
        throw invalid("Malformed batch");
    }

    private static List<ExternalLogEvent> validate(Request request) {
        if (request.events() == null || request.events().isEmpty() || request.events().size() > MAX_EVENTS) {
            throw invalid("Provide between 1 and 10 events");
        }
        var ids = new HashSet<UUID>();
        var events = new ArrayList<ExternalLogEvent>(request.events().size());
        for (ExternalLogEvent event : request.events()) {
            if (event == null || event.eventId() == null || !ids.add(event.eventId())
                    || event.message() == null || event.message().isBlank() || event.level() == null
                    || event.source() == null || event.timestamp() == null || event.timestamp().length() > 40) {
                throw invalid("Missing, invalid or repeated event fields");
            }
            if (bytes(event.message()) + bytes(event.exception()) + 1 > MAX_TEXT_BYTES
                    || bytes(event.loggerName()) > 255 || bytes(event.threadName()) > 100 || bytes(event.hostName()) > 150) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "An event exceeds its field limits");
            }
            String timestamp;
            try {
                var parsed = OffsetDateTime.parse(event.timestamp()).withOffsetSameInstant(ZoneOffset.UTC);
                if (parsed.getYear() < 1 || parsed.getYear() > 9999) {
                    throw invalid("Invalid event timestamp");
                }
                timestamp = parsed.toInstant().toString();
            } catch (DateTimeParseException e) {
                throw invalid("Provide an ISO-8601 timestamp with a zone offset");
            }
            Map<String, String> metadata = event.metadata() == null ? Map.of() : new TreeMap<>(event.metadata());
            if (metadata.size() > 8 || metadata.entrySet().stream().anyMatch(e ->
                    !METADATA_KEY.matcher(e.getKey()).matches() || SENSITIVE_KEY.matcher(e.getKey()).matches()
                            || e.getValue() == null || bytes(e.getValue()) > 256)) {
                throw invalid("Invalid or sensitive event metadata");
            }
            TraceContext trace;
            try {
                trace = TraceContext.parse(event.traceparent(), event.traceOrigin());
            } catch (IllegalArgumentException e) {
                throw invalid("Invalid trace context");
            }
            events.add(new ExternalLogEvent(event.eventId(), timestamp, event.level(), event.source(), event.message(),
                    event.exception(), event.loggerName(), event.threadName(), event.hostName(), event.correlationId(), metadata,
                    trace == null ? null : trace.parent(), trace == null ? null : trace.origin()));
        }
        return events;
    }

    private static int bytes(String value) {
        if (value == null) {
            return 0;
        }
        if (value.indexOf(0) >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw invalid("Invalid event text");
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static byte[] digest(ExternalLogEvent event) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(event));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint event", e);
        }
    }

    private static ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
