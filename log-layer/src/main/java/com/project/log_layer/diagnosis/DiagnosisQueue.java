package com.project.log_layer.diagnosis;

import com.project.log_layer.entity.Log;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.tenant.TenantSqlScope;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DiagnosisQueue {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final TenantSqlScope scope;
    private final DiagnosisSettings settings;

    public enum Failure {
        TRANSPORT, OVERLOADED, UPSTREAM, DEADLINE, INVALID_RESPONSE, REJECTED, CONTEXT, LEASE_EXPIRED
    }

    public record Claim(UUID organization, UUID job, UUID incident, long log, UUID worker, UUID token,
                        int attempt, int maxAttempts) {}
    private record Due(UUID id, UUID incident, long log, String state, int attempt, int maxAttempts, Long actor) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Log log, UUID incident) {
        entities.flush();
        jdbc.update("""
                insert into public.diagnosis_jobs(id,organization_id,incident_id,log_id,max_attempts)
                values (?,?,?,?,?) on conflict (organization_id,log_id) do nothing
                """, UUID.randomUUID(), log.getOrganizationId(), incident, log.getId(), settings.attempts());
    }

    public List<UUID> organizationsAfter(UUID cursor) {
        if (cursor == null) {
            return jdbc.query("select id from public.organizations order by id limit 32",
                    (row, index) -> row.getObject(1, UUID.class));
        }
        return jdbc.query("select id from public.organizations where id>? order by id limit 32",
                (row, index) -> row.getObject(1, UUID.class), cursor);
    }

    @Transactional
    public Claim claim(UUID organization, UUID worker) {
        scope.enter(organization);
        var due = jdbc.query("""
                select id,incident_id,log_id,status,attempt_count,max_attempts,requested_by
                from public.diagnosis_jobs where organization_id=? and
                    ((status in ('PENDING','RETRY_WAIT') and next_attempt_at<=clock_timestamp())
                    or (status='RUNNING' and lease_until<=clock_timestamp()))
                order by coalesce(lease_until,next_attempt_at),id limit 1 for update skip locked
                """, (row, index) -> new Due(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getLong(3), row.getString(4), row.getInt(5), row.getInt(6), row.getObject(7, Long.class)),
                organization).stream().findFirst().orElse(null);
        if (due == null) {
            return null;
        }
        if ("RUNNING".equals(due.state)) {
            attemptClosed(organization, due.id, due.attempt, "LEASE_EXPIRED", Failure.LEASE_EXPIRED, 0);
        }
        if (due.attempt >= due.maxAttempts) {
            jdbc.update("""
                    update public.diagnosis_jobs set status='FAILED_TERMINAL', failure_category='LEASE_EXPIRED',
                    worker_id=null, lease_token=null, lease_until=null, completed_at=clock_timestamp(),
                    updated_at=clock_timestamp() where organization_id=? and id=?
                    """, organization, due.id);
            updateLogState(organization, due.log, "FAILED");
            return null;
        }
        int generation = due.attempt + 1;
        UUID token = UUID.randomUUID();
        jdbc.update("""
                update public.diagnosis_jobs set status='RUNNING', attempt_count=?, worker_id=?, lease_token=?,
                  lease_until=clock_timestamp()+(? * interval '1 millisecond'),
                  last_heartbeat=clock_timestamp(), updated_at=clock_timestamp(), failure_category=null
                where organization_id=? and id=?
                """, generation, worker, token, settings.lease().toMillis(), organization, due.id);
        jdbc.update("""
                insert into public.diagnosis_attempts (organization_id,job_id,attempt,worker_id,requested_by)
                values (?,?,?,?,?)
                """, organization, due.id, generation, worker, due.actor);
        updateLogState(organization, due.log, "PROCESSING");
        return new Claim(organization, due.id, due.incident, due.log, worker, token, generation, due.maxAttempts);
    }

    @Transactional
    public boolean heartbeat(Claim claim) {
        scope.enter(claim.organization);
        return jdbc.update("""
                update public.diagnosis_jobs set last_heartbeat=clock_timestamp(),
                  lease_until=clock_timestamp()+(? * interval '1 millisecond')
                """ + fence(), settings.lease().toMillis(), claim.organization, claim.job, claim.worker,
                claim.token, claim.attempt) == 1;
    }

    @Transactional
    public boolean succeed(Claim claim, LlmAnalysisResponse result) {
        scope.enter(claim.organization);
        if (!finish(claim, "SUCCEEDED", null, 0)) {
            return false;
        }
        if (jdbc.update("""
                update public.logs set llm_summary=?, llm_root_cause=?, llm_recommendation=?, llm_severity=?,
                    llm_analyzed_at=clock_timestamp() at time zone 'UTC', llm_analysis_status='COMPLETED'
                where organization_id=? and id=? and llm_analysis_status is distinct from 'COMPLETED'
                """, result.summary(), result.rootCause(), result.recommendation(), result.severity(),
                claim.organization, claim.log) != 1) {
            throw new IllegalStateException("Diagnosis owner cannot be updated");
        }
        return true;
    }

    @Transactional
    public boolean fail(Claim claim, Failure category, boolean retryable) {
        scope.enter(claim.organization);
        boolean retry = retryable && claim.attempt < claim.maxAttempts;
        long delay = retry ? settings.retryDelay(claim.attempt, ThreadLocalRandom.current().nextDouble(-1, 1)) : 0;
        if (!finish(claim, retry ? "RETRY_WAIT" : "FAILED_TERMINAL", category, delay)) {
            return false;
        }
        updateLogState(claim.organization, claim.log, retry ? "PENDING" : "FAILED");
        return true;
    }

    boolean owns(Claim claim) {
        Boolean valid = jdbc.queryForObject("select exists(select 1 from public.diagnosis_jobs " + fence() + ")",
                Boolean.class, claim.organization, claim.job, claim.worker, claim.token, claim.attempt);
        return Boolean.TRUE.equals(valid);
    }

    /** The caller already verified and set the tenant scope for manual analysis. */
    public String stateForLog(UUID organization, long logId) {
        return jdbc.query("select status from public.diagnosis_jobs where organization_id=? and log_id=?",
                (row, index) -> row.getString(1), organization, logId).stream().findFirst().orElse(null);
    }

    private boolean finish(Claim claim, String state, Failure category, long delay) {
        if (jdbc.update("""
                update public.diagnosis_jobs set status=?, failure_category=?,
                  completed_at=case when ?='RETRY_WAIT' then null else clock_timestamp() end,
                  next_attempt_at=clock_timestamp()+(? * interval '1 millisecond'),
                  worker_id=null,lease_token=null,lease_until=null,updated_at=clock_timestamp()
                """ + fence(), state, category == null ? null : category.name(), state, delay,
                claim.organization, claim.job, claim.worker, claim.token, claim.attempt) != 1) {
            return false;
        }
        attemptClosed(claim.organization, claim.job, claim.attempt, state, category, delay);
        return true;
    }

    private void attemptClosed(UUID organization, UUID job, int attempt, String outcome, Failure failure, long delay) {
        jdbc.update("""
                update public.diagnosis_attempts set finished_at=clock_timestamp(),outcome=?,failure_category=?,
                  next_attempt_at=case when ?='RETRY_WAIT' then clock_timestamp()+(? * interval '1 millisecond') end
                where organization_id=? and job_id=? and attempt=? and finished_at is null
                """, outcome, failure == null ? null : failure.name(), outcome, delay, organization, job, attempt);
    }

    private void updateLogState(UUID organization, long id, String status) {
        jdbc.update("""
                update public.logs set llm_analysis_status=? where organization_id=? and id=?
                  and llm_analysis_status is distinct from 'COMPLETED'
                """, status, organization, id);
    }

    private static String fence() {
        return """
                where organization_id=? and id=? and worker_id=? and lease_token=? and attempt_count=?
                  and status='RUNNING' and lease_until>clock_timestamp()
                """;
    }
}
