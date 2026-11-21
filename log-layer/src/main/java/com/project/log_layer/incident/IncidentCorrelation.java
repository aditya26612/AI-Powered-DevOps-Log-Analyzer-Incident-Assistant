package com.project.log_layer.incident;

import com.project.log_layer.entity.Log;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Source log, incident, membership and diagnosis ownership share the ingestion transaction. */
@Service
@RequiredArgsConstructor
public class IncidentCorrelation {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final IncidentPolicy policy;

    private record Group(UUID id, Long diagnosisOwner, LocalDateTime first) {}
    public record Association(UUID incidentId, boolean ownsDiagnosis) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Association associate(Log log, boolean wantsDiagnosis) {
        var candidate = policy.candidate(log, Instant.now()).orElse(null);
        if (candidate == null) {
            return new Association(null, wantsDiagnosis);
        }
        UUID organization = log.getOrganizationId();
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                Boolean.class, candidate.lockKey(organization)))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Incident group is busy");
        }
        var time = candidate.eventTime();
        Group group = jdbc.query("""
                select id, diagnosis_log_id, first_event_at from public.incidents
                where organization_id = ? and correlation_key = ? and rule_version=2 and status <> 'RESOLVED'
                  and last_event_at >= ? and first_event_at <= ?
                  and first_event_at >= ? and last_event_at <= ?
                order by first_event_at, id limit 1 for update
                """, (rs, row) -> new Group(rs.getObject(1, UUID.class), rs.getObject(2, Long.class),
                rs.getObject(3, LocalDateTime.class)), organization, candidate.key(),
                time.minus(policy.gap()), time.plus(policy.gap()), time.minus(policy.span()), time.plus(policy.span()))
                .stream().findFirst().orElse(null);
        if (!candidate.seed() && (group == null || contextCount(organization, group.id) >= policy.contextLimit())) {
            return new Association(null, false);
        }
        // A Hibernate sequence can allocate an ID before flushing the row required by the composite foreign keys.
        entities.flush();
        boolean ownsDiagnosis = candidate.seed() && wantsDiagnosis && (group == null || group.diagnosisOwner == null);
        Long diagnosisId = ownsDiagnosis ? log.getId() : null;
        UUID incidentId = group == null ? UUID.randomUUID() : group.id;
        String reason;
        if (group == null) {
            reason = "INITIAL_SEED";
            jdbc.update("""
                    insert into public.incidents (id, organization_id, environment, correlation_key, correlation_rule,
                        rule_version, window_start, window_end, first_event_at, last_event_at, severity,
                        event_count, representative_log_id, diagnosis_log_id)
                    values (?, ?, ?, ?, ?, 2, ?, ?, ?, ?, ?, 1, ?, ?)
                    """, incidentId, organization, log.getEnvironment().name(), candidate.key(),
                    candidate.trace() ? "SUPPLIED_TRACE" : "APPLICATION_SIGNATURE", time, windowEnd(time),
                    candidate.eventTime(), candidate.eventTime(), candidate.severity(), log.getId(), diagnosisId);
        } else {
            reason = !candidate.seed() ? "TRACE_CONTEXT"
                    : candidate.trace() ? "SAME_SUPPLIED_TRACE" : "SAME_APPLICATION_SIGNATURE";
            var first = time.isBefore(group.first) ? time : group.first;
            jdbc.update("""
                    update public.incidents set first_event_at=?, window_start=?, window_end=?,
                        last_event_at=greatest(last_event_at,?), severity=greatest(severity,?),
                        event_count=event_count+1, diagnosis_log_id=coalesce(diagnosis_log_id,?),
                        updated_at=now()
                    where organization_id=? and id=?
                    """, first, first, windowEnd(first), time, candidate.severity(), diagnosisId, organization, incidentId);
        }
        jdbc.update("""
                insert into public.incident_memberships (organization_id,incident_id,log_id,reason,seed)
                values (?, ?, ?, ?, ?)
                """, organization, incidentId, log.getId(), reason, candidate.seed());
        return new Association(incidentId, ownsDiagnosis);
    }

    private LocalDateTime windowEnd(LocalDateTime first) {
        // The retained V6 constraint uses an exclusive upper bound at database precision.
        return first.plus(policy.span()).plusNanos(1000);
    }

    private long contextCount(UUID organization, UUID group) {
        return jdbc.queryForObject("""
                select count(*) from public.incident_memberships
                where organization_id=? and incident_id=? and not seed
                """, Long.class, organization, group);
    }
}
