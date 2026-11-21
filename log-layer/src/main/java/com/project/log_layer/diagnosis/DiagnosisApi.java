package com.project.log_layer.diagnosis;

import com.project.log_layer.security.TenantIdentity;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantSqlScope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organization}")
@RequiredArgsConstructor
public class DiagnosisApi {
    private final Access access;

    @GetMapping("/incidents/{incident}/diagnosis")
    JobView incident(@PathVariable UUID organization, @PathVariable UUID incident,
                     @RequestParam(defaultValue = "0") int page, HttpServletRequest request) {
        return access.read(organization, TenantIdentity.requireUserId(request), incident, null, page);
    }

    @GetMapping("/logs/{log}/diagnosis")
    JobView log(@PathVariable UUID organization, @PathVariable long log,
                @RequestParam(defaultValue = "0") int page, HttpServletRequest request) {
        return access.read(organization, TenantIdentity.requireUserId(request), null, log, page);
    }

    @PostMapping("/incidents/{incident}/diagnosis/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    JobView retryIncident(@PathVariable UUID organization, @PathVariable UUID incident, HttpServletRequest request) {
        return access.retry(organization, TenantIdentity.requireUserId(request), incident, null);
    }

    @PostMapping("/logs/{log}/diagnosis/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    JobView retryLog(@PathVariable UUID organization, @PathVariable long log, HttpServletRequest request) {
        return access.retry(organization, TenantIdentity.requireUserId(request), null, log);
    }

    public record Attempt(int number, OffsetDateTime startedAt, OffsetDateTime finishedAt, String outcome,
                          String failureCategory, OffsetDateTime nextAttemptAt, String requestedBy) {}
    public record JobView(UUID id, String logId, String status, int attemptCount, int maxAttempts,
                          int manualRetries, OffsetDateTime nextAttemptAt, String failureCategory, String summary,
                          boolean leaseExpired, List<Attempt> attempts, boolean hasMore) {}
    private record Job(UUID id, long log, String status, int attemptCount, int maxAttempts, int manualRetries,
                       OffsetDateTime nextAttempt, String failureCategory, String summary, boolean leaseExpired) {}

    @Service
    @RequiredArgsConstructor
    public static class Access {
        private static final int SIZE = 10;
        private final JdbcTemplate jdbc;
        private final OrganizationService organizations;
        private final TenantSqlScope scope;
        private final DiagnosisSettings settings;

        @Transactional(readOnly = true)
        public JobView read(UUID org, long actor, UUID incident, Long log, int page) {
            enter(org, actor, false);
            if (page < 0 || page > 24) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid diagnosis attempt page");
            }
            return view(org, find(org, incident, log), page);
        }

        @Transactional
        public JobView retry(UUID org, long actor, UUID incident, Long log) {
            enter(org, actor, true);
            Job job = find(org, incident, log);
            int changed = jdbc.update("""
                    update public.diagnosis_jobs set status='PENDING', next_attempt_at=clock_timestamp(),
                       completed_at=null,failure_category=null,manual_retries=manual_retries+1,
                       max_attempts=attempt_count+?,requested_by=?,updated_at=clock_timestamp()
                    where organization_id=? and id=? and status='FAILED_TERMINAL' and manual_retries<3
                    """, settings.attempts(), actor, org, job.id);
            if (changed != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Diagnosis cannot be retried");
            }
            jdbc.update("""
                    update public.logs set llm_analysis_status='PENDING'
                    where organization_id=? and id=? and llm_analysis_status is distinct from 'COMPLETED'
                    """, org, job.log);
            return view(org, find(org, incident, log), 0);
        }

        private void enter(UUID org, long actor, boolean write) {
            if (write) {
                if (!organizations.get(org, actor).role().managesApplications()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient diagnosis permissions");
                }
            } else {
                organizations.get(org, actor);
            }
            scope.enter(org);
        }

        private Job find(UUID org, UUID incident, Long log) {
            return jdbc.query("""
                    select j.id,j.log_id,j.status,j.attempt_count,j.max_attempts,j.manual_retries,
                      j.next_attempt_at,j.failure_category,
                      case when j.status='SUCCEEDED' then l.llm_summary end,
                      j.status='RUNNING' and j.lease_until<=clock_timestamp()
                    from public.diagnosis_jobs j join public.logs l on l.organization_id=j.organization_id and l.id=j.log_id
                    where j.organization_id=? and
                    """ + (incident != null ? "j.incident_id=?" : "j.log_id=?"),
                    (row,index) -> new Job(row.getObject(1,UUID.class),row.getLong(2),row.getString(3),
                        row.getInt(4),row.getInt(5),row.getInt(6),row.getObject(7,OffsetDateTime.class),
                        row.getString(8),row.getString(9),row.getBoolean(10)), org, incident != null ? incident : log)
                    .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found"));
        }

        private JobView view(UUID org, Job job, int page) {
            var attempts = jdbc.query("""
                    select attempt,started_at,finished_at,outcome,failure_category,next_attempt_at,requested_by
                    from public.diagnosis_attempts where organization_id=? and job_id=?
                    order by attempt desc limit ? offset ?
                    """, (row,index) -> new Attempt(row.getInt(1),row.getObject(2,OffsetDateTime.class),
                    row.getObject(3,OffsetDateTime.class),row.getString(4),row.getString(5),
                    row.getObject(6,OffsetDateTime.class),row.getString(7)), org, job.id, SIZE + 1, page * SIZE);
            OffsetDateTime due = "PENDING".equals(job.status) || "RETRY_WAIT".equals(job.status) ? job.nextAttempt : null;
            return new JobView(job.id, Long.toString(job.log), job.status, job.attemptCount, job.maxAttempts, job.manualRetries,
                    due, job.failureCategory, job.summary, job.leaseExpired,
                    List.copyOf(attempts.subList(0, Math.min(SIZE, attempts.size()))),
                    attempts.size() > SIZE);
        }
    }
}
