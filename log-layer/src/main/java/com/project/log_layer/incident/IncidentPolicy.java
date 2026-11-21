package com.project.log_layer.incident;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.service.OperationalTriage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class IncidentPolicy {
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final Pattern REQUEST_ID = Pattern.compile(
            "(?i)\\b(request[-_ ]?id[=:]\\h*)[a-z0-9_-]{1,80}\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private final Duration gap;
    private final Duration span;
    private final Duration age;
    private final Duration future;
    private final int contextLimit;

    public IncidentPolicy(@Value("${incident.rolling-gap:5m}") Duration gap,
                          @Value("${incident.max-span:30m}") Duration span,
                          @Value("${incident.max-event-age:45m}") Duration age,
                          @Value("${incident.future-skew:1m}") Duration future,
                          @Value("${incident.context-limit:10}") int contextLimit) {
        if (gap.compareTo(Duration.ofSeconds(1)) < 0 || gap.compareTo(Duration.ofMinutes(15)) > 0
                || span.compareTo(gap) < 0 || span.compareTo(Duration.ofHours(2)) > 0
                || age.compareTo(gap) < 0 || age.compareTo(Duration.ofHours(4)) > 0
                || gap.getNano() != 0 || span.getNano() != 0
                || future.isNegative() || future.compareTo(Duration.ofMinutes(5)) > 0
                || contextLimit < 0 || contextLimit > 30) {
            throw new IllegalArgumentException("Incident policy settings outside supported bounds");
        }
        this.gap = gap;
        this.span = span;
        this.age = age;
        this.future = future;
        this.contextLimit = contextLimit;
    }

    record Candidate(byte[] key, boolean trace, short severity, LocalDateTime eventTime) {
        boolean seed() { return severity != 0; }
        String lockKey(UUID organization) {
            return "devinsight.incident:v2:" + organization + ":" + HexFormat.of().formatHex(key);
        }
    }

    Optional<Candidate> candidate(Log log, Instant now) {
        if (log.getOrganizationId() == null || log.getApplicationId() == null || log.getExternalEventId() == null) {
            return Optional.empty();
        }
        // PostgreSQL stores logged timestamps at microsecond precision.
        LocalDateTime storedTime = log.getTimestamp().plusNanos(500).truncatedTo(ChronoUnit.MICROS);
        Instant time = storedTime.toInstant(ZoneOffset.UTC);
        if (time.isAfter(now.plus(future)) || time.isBefore(now.minus(age))) {
            return Optional.empty();
        }
        String w3c = log.getTraceparent();
        boolean uuid = log.getCorrelationOrigin() == Log.CorrelationOrigin.COLLECTOR_SUPPLIED
                && log.getCorrelationId() != null && !ZERO_UUID.equals(log.getCorrelationId());
        boolean trace = w3c != null || uuid;
        short severity = (short) (log.getLevel() == LogLevel.FATAL ? 3 : log.getLevel() == LogLevel.ERROR ? 2
                : Boolean.TRUE.equals(OperationalTriage.statisticalAnomaly(log)) ? 1 : 0);
        if (severity == 0 && (!trace || contextLimit == 0
                || !Boolean.FALSE.equals(OperationalTriage.statisticalAnomaly(log)))) {
            return Optional.empty();
        }
        String evidence = w3c != null ? "w3c:" + w3c.substring(3, 35)
                : uuid ? "uuid:" + log.getCorrelationId()
                : "application:" + log.getApplicationId() + "\n" + normalize(log.getMessage()) + "\n"
                + normalize(headline(log.externalException()));
        byte[] key = digest("v2:" + gap.getSeconds() + ":" + span.getSeconds() + ":" + log.getEnvironment() + "\n" + evidence);
        return Optional.of(new Candidate(key, trace, severity, storedTime));
    }

    int contextLimit() { return contextLimit; }
    Duration gap() { return gap; }
    Duration span() { return span; }

    private static String headline(String exception) {
        return exception == null ? "" : exception.lines().findFirst().orElse("");
    }

    private static String normalize(String input) {
        String value = REQUEST_ID.matcher(input).replaceAll("$1<id>");
        return WHITESPACE.matcher(value).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
