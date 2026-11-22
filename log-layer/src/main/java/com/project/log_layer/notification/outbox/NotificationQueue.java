package com.project.log_layer.notification.outbox;

import com.project.log_layer.tenant.TenantSqlScope;
import com.project.log_layer.tenant.WebhookCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class NotificationQueue {
    private final JdbcTemplate jdbc;
    private final TenantSqlScope scope;
    private final WebhookCipher cipher;
    private final DeliverySettings settings;

    public record Claim(UUID organization, UUID id, long application, UUID worker, UUID token, int attempt,
                        byte[] destinationDigest) {}
    record Work(String destination, SlackMessage.Evidence evidence) {
        @Override
        public String toString() {
            return "Slack delivery work";
        }
    }
    record Result(String status, String category, Integer httpStatus, long retryAfterSeconds) {}
    private record Due(UUID id, Long application, String status, int attempt, int limit, Long actor,
                       boolean dispatched, String ciphertext, UUID token) {}
    private record Bound(UUID incident, long log, long revision, int severity, boolean expired) {}
    private record Destination(long revision, boolean enabled, String ciphertext, String name, String environment) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID organization, UUID job, UUID incident, long log) {
        if (settings.transport() == DeliverySettings.Transport.OFF) {
            return;
        }
        scope.enter(organization);
        jdbc.update("""
                with evidence as (
                    select l.id,l.application_id,a.slack_delivery_revision,
                      coalesce(i.severity,case l.level when 'FATAL' then 3 when 'ERROR' then 2 else 1 end) severity,
                      a.slack_minimum_severity,
                      case
                        when a.id is null or a.slack_webhook_ciphertext is null then 'DESTINATION_MISSING'
                        when a.deactivated_at is not null or not a.slack_delivery_enabled then 'DESTINATION_DISABLED'
                        when i.status='RESOLVED' then 'INCIDENT_RESOLVED'
                        when not (coalesce(l.level in ('ERROR','FATAL') and a.alert_on_error,false)
                           or coalesce(l.anomaly and l.analysis_status='COMPLETED' and a.alert_on_anomaly,false))
                        then 'ALERT_PREFERENCE'
                      end suppression
                    from public.logs l left join public.application_integrations a
                      on a.organization_id=l.organization_id and a.id=l.application_id
                    left join public.incidents i on i.organization_id=l.organization_id and i.id=?
                    where l.organization_id=? and l.id=?
                ), routed as (
                    select *,coalesce(suppression,case when severity<slack_minimum_severity
                                                      then 'SEVERITY_PREFERENCE' end) reason from evidence
                )
                insert into public.notification_outbox
                    (id,organization_id,diagnosis_id,incident_id,log_id,application_id,destination_revision,severity,
                     status,failure_category,max_attempts,expires_at)
                select ?,?,?,?,id,application_id,slack_delivery_revision,severity,
                       case when reason is null then 'PENDING' else 'SKIPPED' end,reason,?,
                       clock_timestamp()+(? * interval '1 millisecond') from routed
                on conflict do nothing
                """, incident, organization, log, UUID.randomUUID(), organization, job, incident,
                settings.attempts(), settings.lifetime().toMillis());
    }

    @Transactional
    public Claim claim(UUID organization, UUID worker) {
        scope.enter(organization);
        Due due = jdbc.query("""
                select n.id,n.application_id,n.status,n.attempt_count,n.max_attempts,n.requested_by,
                    n.dispatch_started_at is not null,
                    case when a.slack_delivery_revision=n.destination_revision and a.slack_delivery_enabled
                         and a.deactivated_at is null and n.expires_at>clock_timestamp()
                         then a.slack_webhook_ciphertext end, n.lease_token
                from public.notification_outbox n join public.application_integrations a
                    on a.organization_id=n.organization_id and a.id=n.application_id
                where n.organization_id=? and (
                    (n.status='SENDING' and n.lease_until<=clock_timestamp())
                    or (n.status in ('PENDING','RETRY_WAIT') and n.next_attempt_at<=clock_timestamp()
                        and (a.slack_not_before is null or a.slack_not_before<=clock_timestamp() or n.expires_at<=clock_timestamp())
                        and not exists (select 1 from public.notification_outbox active
                            where active.organization_id=n.organization_id and active.application_id=n.application_id
                              and active.status='SENDING')))
                order by coalesce(n.lease_until,n.next_attempt_at),n.id limit 1 for update of n skip locked
                """, (row,index) -> new Due(row.getObject(1,UUID.class),row.getObject(2,Long.class),row.getString(3),
                row.getInt(4),row.getInt(5),row.getObject(6,Long.class),row.getBoolean(7),row.getString(8),
                row.getObject(9,UUID.class)),organization)
                .stream().findFirst().orElse(null);
        if (due == null || !destinationLock(organization,due.application)) {
            return null;
        }
        if ("SENDING".equals(due.status)) {
            recover(organization,due);
            return null;
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from public.notification_outbox n
                              where n.organization_id=? and n.application_id=? and n.status='SENDING')
                """, Boolean.class, organization,due.application))) {
            return null;
        }
        UUID token = UUID.randomUUID();
        byte[] digest = destinationDigest(organization,due);
        if (digest != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select public.reserve_notification_destination(?,?,?)",Boolean.class,digest,token,gateLeaseMillis()))) {
            jdbc.update("""
                    update public.notification_outbox set next_attempt_at=clock_timestamp()+interval '1 second'
                    where organization_id=? and id=?
                    """,organization,due.id);
            return null;
        }
        int generation = due.attempt + 1;
        jdbc.update("""
                update public.notification_outbox set status='SENDING',attempt_count=?,worker_id=?,lease_token=?,
                  lease_until=clock_timestamp()+(? * interval '1 millisecond'), updated_at=clock_timestamp()
                where organization_id=? and id=?
                """, generation,worker,token,settings.lease().toMillis(),organization,due.id);
        jdbc.update("""
                insert into public.notification_attempts(organization_id,notification_id,attempt,worker_id,requested_by)
                values (?,?,?,?,?)
                """, organization,due.id,generation,worker,due.actor);
        return new Claim(organization,due.id,due.application,worker,token,generation,digest);
    }

    private byte[] destinationDigest(UUID organization, Due due) {
        if (due.ciphertext == null) {
            return null;
        }
        try {
            return cipher.destinationDigest(cipher.decrypt(organization,due.application,due.ciphertext));
        } catch (IllegalStateException | ResponseStatusException ex) {
            // The dispatch eligibility transaction records invalid destination evidence.
            return null;
        }
    }

    private long gateLeaseMillis() {
        return settings.lease().toMillis()+1000;
    }

    private boolean updateGate(Claim claim, long delayMillis, boolean release) {
        return updateGate(claim.destinationDigest,claim.token,delayMillis,release);
    }

    private boolean updateGate(byte[] digest, UUID token, long delayMillis, boolean release) {
        return digest == null || Boolean.TRUE.equals(jdbc.queryForObject(
                "select public.update_notification_destination(?,?,?,?)",Boolean.class,
                digest,token,delayMillis,release));
    }

    private boolean destinationLock(UUID organization, long application) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                Boolean.class,"devinsight.slack:" + organization + ":" + application));
    }

    private void recover(UUID organization, Due due) {
        boolean retry = !due.dispatched && due.attempt < due.limit;
        String state = due.dispatched ? "DELIVERY_UNKNOWN" : retry ? "RETRY_WAIT" : "FAILED_TERMINAL";
        String reason = due.dispatched ? "LEASE_EXPIRED_AFTER_DISPATCH" : "LEASE_EXPIRED_BEFORE_DISPATCH";
        jdbc.update("""
                update public.notification_outbox set status=?,failure_category=?,worker_id=null,lease_token=null,
                  lease_until=null,dispatch_started_at=null,next_attempt_at=clock_timestamp(),updated_at=clock_timestamp()
                where organization_id=? and id=?
                """, state,reason,organization,due.id);
        closeAttempt(organization,due.id,due.attempt,retry ? "ABANDONED" : state,reason,null,0);
        if (!due.dispatched) {
            updateGate(destinationDigest(organization,due),due.token,0,true);
        }
    }

    @Transactional
    public Work begin(Claim claim) {
        scope.enter(claim.organization);
        Bound bound = jdbc.query("""
                select incident_id,log_id,destination_revision,severity,expires_at<=clock_timestamp()
                from public.notification_outbox
                """ + fence() + " and dispatch_started_at is null for update", (row,index) ->
                new Bound(row.getObject(1,UUID.class),row.getLong(2),row.getLong(3),row.getInt(4),row.getBoolean(5)),
                claim.organization,claim.id,claim.worker,claim.token,claim.attempt).stream().findFirst().orElse(null);
        if (bound == null) {
            return null;
        }
        if (bound.expired) {
            cancel(claim,"EXPIRED");
            return null;
        }
        if (bound.incident != null && "RESOLVED".equals(jdbc.queryForObject("""
                select status from public.incidents where organization_id=? and id=? for share
                """, String.class,claim.organization,bound.incident))) {
            cancel(claim,"INCIDENT_RESOLVED");
            return null;
        }
        Destination target = jdbc.query("""
                select slack_delivery_revision,slack_delivery_enabled and deactivated_at is null,
                       slack_webhook_ciphertext,application_name,environment
                from public.application_integrations where organization_id=? and id=? for update
                """, (row,index) -> new Destination(row.getLong(1),row.getBoolean(2),row.getString(3),
                row.getString(4),row.getString(5)),claim.organization,claim.application).stream().findFirst().orElse(null);
        if (target == null || !target.enabled || target.ciphertext == null || target.revision != bound.revision) {
            cancel(claim,"DESTINATION_CHANGED");
            return null;
        }
        String secret;
        try {
            secret = cipher.decrypt(claim.organization,claim.application,target.ciphertext);
        } catch (IllegalStateException | ResponseStatusException ex) {
            finishInScope(claim,new Result("FAILED_TERMINAL","DESTINATION_INVALID",null,0));
            return null;
        }
        if (claim.destinationDigest == null || !updateGate(claim,gateLeaseMillis(),false)) {
            finishInScope(claim,new Result("RETRY_WAIT","DESTINATION_LEASE_EXPIRED",null,0));
            return null;
        }
        if (settings.transport() == DeliverySettings.Transport.FIXTURE
                && !Boolean.TRUE.equals(jdbc.queryForObject("""
                    select inet_server_port()=15438 and host(inet_server_addr())='127.0.0.1'
                    """,Boolean.class))) {
            cancel(claim,"FIXTURE_DATABASE_REQUIRED");
            return null;
        }

        var evidence = jdbc.queryForObject("""
                select l.llm_summary,l.llm_root_cause,l.llm_recommendation,i.status,
                  i.first_event_at,i.last_event_at,i.event_count,l.timestamp,
                  exists (select 1 from public.incident_memberships m join public.logs related
                      on related.organization_id=m.organization_id and related.id=m.log_id
                      where m.organization_id=l.organization_id and m.incident_id=i.id
                        and related.application_id is distinct from l.application_id)
                from public.logs l left join public.incidents i on i.organization_id=l.organization_id and i.id=?
                where l.organization_id=? and l.id=? and l.application_id=? and l.llm_analysis_status='COMPLETED'
                """, (row,index) -> new SlackMessage.Evidence(claim.id,bound.incident,bound.log,target.name,
                target.environment,bound.severity,row.getString(4),row.getString(5),row.getString(6),
                row.getLong(7),row.getString(8),row.getString(1),row.getString(2),row.getString(3),row.getBoolean(9)),
                bound.incident,claim.organization,bound.log,claim.application);
        if (jdbc.update("""
                update public.notification_outbox set dispatch_started_at=clock_timestamp(),updated_at=clock_timestamp(),
                    lease_until=clock_timestamp()+(? * interval '1 millisecond')
                """ + fence(),settings.lease().toMillis(),claim.organization,claim.id,claim.worker,claim.token,claim.attempt) != 1) {
            return null;
        }
        return new Work(secret,evidence);
    }

    @Transactional
    public boolean heartbeat(Claim claim) {
        scope.enter(claim.organization);
        if (jdbc.update("""
                update public.notification_outbox set lease_until=clock_timestamp()+(? * interval '1 millisecond')
                """ + fence(),settings.lease().toMillis(),claim.organization,claim.id,claim.worker,claim.token,claim.attempt) != 1) {
            return false;
        }
        if (!updateGate(claim,gateLeaseMillis(),false)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
        return true;
    }

    @Transactional
    public boolean finish(Claim claim, Result result) {
        scope.enter(claim.organization);
        return finishInScope(claim,result);
    }

    private boolean finishInScope(Claim claim, Result result) {
        var budget = jdbc.query("""
                select max_attempts,extract(epoch from expires_at-clock_timestamp())::bigint
                from public.notification_outbox
                """ + fence() + " for update", (row,index) -> new long[]{row.getInt(1),row.getLong(2)},
                claim.organization,claim.id,claim.worker,claim.token,claim.attempt).stream().findFirst().orElse(null);
        if (budget == null) {
            return false;
        }
        String state = result.status;
        String reason = result.category;
        long delay = 0;
        if ("RETRY_WAIT".equals(state)) {
            if (claim.attempt >= budget[0]) {
                state = "FAILED_TERMINAL";
                reason = "ATTEMPTS_EXHAUSTED";
            } else {
                delay = Math.max(result.retryAfterSeconds,Math.min(120L,2L << Math.min(claim.attempt-1,5))
                        + ThreadLocalRandom.current().nextInt(3));
                if (delay >= budget[1]) {
                    state = "FAILED_TERMINAL";
                    reason = "RETRY_BEYOND_LIFETIME";
                    delay = 0;
                }
            }
        }
        if (jdbc.update("""
                update public.notification_outbox set status=?,failure_category=?,worker_id=null,lease_token=null,
                  lease_until=null,dispatch_started_at=null,updated_at=clock_timestamp(),
                  next_attempt_at=clock_timestamp()+(? * interval '1 second'),
                  acknowledged_at=case when ?='DELIVERED' then clock_timestamp() end
                """ + fence(),state,reason,delay,state,claim.organization,claim.id,claim.worker,claim.token,claim.attempt) != 1) {
            return false;
        }
        // Keep the cooldown for this registered destination even when the job terminates.
        long providerDelay = Integer.valueOf(429).equals(result.httpStatus) ? Math.max(2,result.retryAfterSeconds)
                : "DELIVERED".equals(state) ? 1 : 0;
        if (providerDelay > 0) {
            jdbc.update("""
                    update public.application_integrations
                    set slack_not_before=greatest(slack_not_before,
                        clock_timestamp()+(? * interval '1 second'))
                    where organization_id=? and id=? and slack_delivery_revision=(select destination_revision
                         from public.notification_outbox where organization_id=? and id=?)
                    """, providerDelay,claim.organization,claim.application,claim.organization,claim.id);
        }
        updateGate(claim,providerDelay*1000,true);
        closeAttempt(claim.organization,claim.id,claim.attempt,state,reason,result.httpStatus,delay);
        return true;
    }

    private void cancel(Claim claim, String reason) {
        finishInScope(claim,new Result("CANCELLED",reason,null,0));
    }

    private void closeAttempt(UUID organization, UUID id, int attempt, String state, String reason,
                              Integer status, long delay) {
        if (jdbc.update("""
                update public.notification_attempts set outcome=?,failure_category=?,http_status=?,
                  finished_at=clock_timestamp(),next_attempt_at=case when ?='RETRY_WAIT'
                    then clock_timestamp()+(? * interval '1 second') end
                where organization_id=? and notification_id=? and attempt=? and finished_at is null
                """,state,reason,status,state,delay,organization,id,attempt) != 1) {
            throw new IllegalStateException("Notification attempt history is missing");
        }
    }

    private static String fence() {
        return """
                where organization_id=? and id=? and worker_id=? and lease_token=? and attempt_count=?
                  and status='SENDING' and lease_until>clock_timestamp()
                """;
    }
}
