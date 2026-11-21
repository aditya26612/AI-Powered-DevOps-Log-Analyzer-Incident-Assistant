package com.project.log_layer.incident;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-21T12:04:50Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID TRACE = UUID.randomUUID();
    private final IncidentPolicy policy = new IncidentPolicy(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofMinutes(45),
            Duration.ofMinutes(1), 10);

    @Test
    void tracedFailuresShareKeyAcrossApplicationsButNotEnvironmentsOrTenants() {
        var first = log("2026-11-21T12:04:01Z", "Redis connection refused", LogLevel.ERROR);
        var second = log("2026-11-21T12:00:10Z", "Cache dependency failed", LogLevel.ERROR);
        second.setApplicationId(2L);
        assertArrayEquals(candidate(first).key(), candidate(second).key());
        assertArrayEquals(candidate(first).key(), candidate(second).key());
        second.setOrganizationId(UUID.randomUUID());
        assertNotEquals(candidate(first).lockKey(ORG), candidate(second).lockKey(second.getOrganizationId()));
        second.setEnvironment(Environment.PRODUCTION);
        assertFalse(java.util.Arrays.equals(candidate(first).key(), candidate(second).key()));
    }

    @Test
    void generatedAndLegacyIdsCanOnlyUseApplicationFailureEvidence() {
        var first = log("2026-11-21T12:04:01Z", "HTTP 503 request-id=a1 on /checkout", LogLevel.ERROR);
        var similar = log("2026-11-21T12:04:02Z", "HTTP 503 request-id=b2 on /checkout", LogLevel.ERROR);
        first.setCorrelationOrigin(Log.CorrelationOrigin.SERVER_GENERATED);
        similar.setCorrelationOrigin(Log.CorrelationOrigin.LEGACY_SUPPLIED);
        assertFalse(candidate(first).trace());
        assertArrayEquals(candidate(first).key(), candidate(similar).key());
        similar.setMessage("HTTP 401 request-id=b2 on /checkout");
        assertFalse(java.util.Arrays.equals(candidate(first).key(), candidate(similar).key()));
        similar.setMessage(first.getMessage());
        similar.setApplicationId(2L);
        assertFalse(java.util.Arrays.equals(candidate(first).key(), candidate(similar).key()));
    }

    @Test
    void boundariesGraceAndFutureSkewAreExplicit() {
        var older = log("2026-11-21T12:04:59Z", "failure", LogLevel.ERROR);
        var next = log("2026-11-21T12:05:00Z", "failure", LogLevel.ERROR);
        assertArrayEquals(candidate(older).key(), candidate(next).key());
        assertTrue(policy.candidate(older, Instant.parse("2026-11-21T12:49:59Z")).isPresent());
        assertTrue(policy.candidate(older, Instant.parse("2026-11-21T12:50:00Z")).isEmpty());
        next.setTimestamp(LocalDateTime.parse("2026-11-21T12:05:51"));
        assertTrue(policy.candidate(next, NOW).isEmpty());
        assertEquals(LocalDateTime.parse("2026-11-21T12:05:51"), next.getTimestamp());
    }

    @Test
    void contextOutagesAndUnassignedLogsKeepDistinctEligibility() {
        var log = log("2026-11-21T12:04:01Z", "dependency report", LogLevel.INFO);
        assertFalse(candidate(log).seed());
        log.setAnalysisStatus(AnalysisStatus.FAILED);
        log.setAnomaly(true);
        assertTrue(policy.candidate(log, NOW).isEmpty());
        log.setLevel(LogLevel.FATAL);
        assertEquals(3, candidate(log).severity());
        log.setOrganizationId(null);
        assertTrue(policy.candidate(log, NOW).isEmpty());
    }

    private IncidentPolicy.Candidate candidate(Log log) {
        return policy.candidate(log, NOW).orElseThrow();
    }

    private static Log log(String timestamp, String message, LogLevel level) {
        return Log.builder().organizationId(ORG).applicationId(1L).environment(Environment.DEVELOPMENT)
                .externalEventId(UUID.randomUUID()).timestamp(LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneOffset.UTC))
                .message(message).level(level).correlationOrigin(Log.CorrelationOrigin.COLLECTOR_SUPPLIED)
                .correlationId(TRACE).analysisStatus(AnalysisStatus.COMPLETED).build();
    }
}
