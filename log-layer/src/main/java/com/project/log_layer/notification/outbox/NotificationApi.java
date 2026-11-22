package com.project.log_layer.notification.outbox;

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
public class NotificationApi {
    private final Access access;

    @GetMapping("/incidents/{incident}/notifications")
    Page<Delivery> incident(@PathVariable UUID organization,@PathVariable UUID incident,
                            @RequestParam(defaultValue="0") int page,HttpServletRequest request) {
        return access.incident(organization,TenantIdentity.requireUserId(request),incident,page);
    }

    @GetMapping("/notifications/{notification}")
    Delivery delivery(@PathVariable UUID organization,@PathVariable UUID notification,HttpServletRequest request) {
        return access.delivery(organization,TenantIdentity.requireUserId(request),notification);
    }

    @GetMapping("/notifications/{notification}/attempts")
    Page<Attempt> attempts(@PathVariable UUID organization,@PathVariable UUID notification,
                           @RequestParam(defaultValue="0") int page,HttpServletRequest request) {
        return access.attempts(organization,TenantIdentity.requireUserId(request),notification,page);
    }

    @PostMapping("/notifications/{notification}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Delivery retry(@PathVariable UUID organization,@PathVariable UUID notification,
                   @RequestBody(required=false) Retry retry,HttpServletRequest request) {
        return access.retry(organization,TenantIdentity.requireUserId(request),notification,
                retry != null && retry.confirmPossibleDuplicate);
    }

    public record Retry(boolean confirmPossibleDuplicate) {}
    public record Page<T>(List<T> content,int page,boolean hasMore) {}
    public record Delivery(UUID id,UUID incidentId,String logId,String destinationLabel,String status,
                           int attemptCount,int maxAttempts,int manualRetries,OffsetDateTime createdAt,
                           OffsetDateTime lastAttemptAt,OffsetDateTime acknowledgedAt,OffsetDateTime nextAttemptAt,
                           String failureCategory,boolean operatorReviewRequired) {}
    public record Attempt(int number,OffsetDateTime startedAt,OffsetDateTime finishedAt,String outcome,
                          Integer httpStatus,String failureCategory,OffsetDateTime nextAttemptAt,String requestedBy) {}

    @Service
    @RequiredArgsConstructor
    public static class Access {
        private static final int SIZE=10;
        private static final String VIEWS = """
                select n.id,n.incident_id,n.log_id,a.application_name,n.status,n.attempt_count,n.max_attempts,
                  n.manual_retries,n.created_at,last.started_at,n.acknowledged_at,
                  case when n.status='RETRY_WAIT' then n.next_attempt_at end,n.failure_category
                from public.notification_outbox n left join public.application_integrations a
                  on a.organization_id=n.organization_id and a.id=n.application_id
                left join lateral (select started_at from public.notification_attempts na
                    where na.organization_id=n.organization_id and na.notification_id=n.id
                    order by attempt desc limit 1) last on true
                where n.organization_id=?
                """;
        private final JdbcTemplate jdbc;
        private final OrganizationService organizations;
        private final TenantSqlScope scope;
        private final DeliverySettings settings;

        @Transactional(readOnly=true)
        public Page<Delivery> incident(UUID org,long actor,UUID incident,int page) {
            enter(org,actor,false);
            verifyPage(page);
            if (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from public.incidents where organization_id=? and id=?)",
                    Boolean.class,org,incident))) {
                throw missing();
            }
            return page(views(" and n.incident_id=? order by n.created_at desc,n.id limit ? offset ?",
                    org,incident,SIZE+1,page*SIZE),page);
        }

        @Transactional(readOnly=true)
        public Delivery delivery(UUID org,long actor,UUID id) {
            enter(org,actor,false);
            return find(org,id);
        }

        @Transactional(readOnly=true)
        public Page<Attempt> attempts(UUID org,long actor,UUID id,int page) {
            enter(org,actor,false);
            verifyPage(page);
            find(org,id);
            return page(jdbc.query("""
                    select attempt,started_at,finished_at,outcome,http_status,failure_category,next_attempt_at,requested_by
                    from public.notification_attempts where organization_id=? and notification_id=?
                    order by attempt desc limit ? offset ?
                    """,(row,index)->new Attempt(row.getInt(1),row.getObject(2,OffsetDateTime.class),
                    row.getObject(3,OffsetDateTime.class),row.getString(4),row.getObject(5,Integer.class),
                    row.getString(6),row.getObject(7,OffsetDateTime.class),row.getString(8)),org,id,SIZE+1,page*SIZE),page);
        }

        @Transactional
        public Delivery retry(UUID org,long actor,UUID id,boolean confirmed) {
            enter(org,actor,true);
            find(org,id);
            if (settings.transport()==DeliverySettings.Transport.OFF || jdbc.update("""
                    update public.notification_outbox n set status='PENDING',failure_category=null,
                      next_attempt_at=clock_timestamp(),updated_at=clock_timestamp(),requested_by=?,
                      manual_retries=n.manual_retries+1,max_attempts=least(15,n.attempt_count+?)
                    from public.application_integrations a
                    where n.organization_id=? and n.id=? and n.manual_retries<2
                      and n.expires_at>clock_timestamp()
                      and (n.status='FAILED_TERMINAL' or (n.status='DELIVERY_UNKNOWN' and ?))
                      and a.organization_id=n.organization_id and a.id=n.application_id
                      and a.slack_delivery_enabled and a.deactivated_at is null
                      and a.slack_webhook_ciphertext is not null and a.slack_delivery_revision=n.destination_revision
                      and not exists(select 1 from public.incidents i where i.organization_id=n.organization_id
                                      and i.id=n.incident_id and i.status='RESOLVED')
                    """,actor,settings.attempts(),org,id,confirmed)!=1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Notification cannot be retried without a current destination and any required confirmation");
            }
            return find(org,id);
        }

        private Delivery find(UUID org,UUID id) {
            return views(" and n.id=?",org,id).stream().findFirst().orElseThrow(Access::missing);
        }

        private List<Delivery> views(String clause,Object... params) {
            return jdbc.query(VIEWS+clause,(row,index)->new Delivery(row.getObject(1,UUID.class),
                    row.getObject(2,UUID.class),row.getString(3),row.getString(4),row.getString(5),row.getInt(6),
                    row.getInt(7),row.getInt(8),row.getObject(9,OffsetDateTime.class),row.getObject(10,OffsetDateTime.class),
                    row.getObject(11,OffsetDateTime.class),row.getObject(12,OffsetDateTime.class),row.getString(13),
                    "DELIVERY_UNKNOWN".equals(row.getString(5))),params);
        }

        private void enter(UUID org,long actor,boolean write) {
            var role=organizations.get(org,actor).role();
            if (write && !role.managesApplications()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Insufficient notification permissions");
            }
            scope.enter(org);
        }

        private static void verifyPage(int page) {
            if (page<0 || page>100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid notification page");
            }
        }

        private static <T> Page<T> page(List<T> results,int number) {
            return new Page<>(results.subList(0,Math.min(results.size(),SIZE)),number,results.size()>SIZE);
        }

        private static ResponseStatusException missing() {
            return new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
        }
    }
}
