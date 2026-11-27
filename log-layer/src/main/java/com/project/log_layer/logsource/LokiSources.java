package com.project.log_layer.logsource;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantApplications;
import com.project.log_layer.tenant.TenantSqlScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class LokiSources {
    static final long SECOND = 1_000_000_000L;
    static final long WINDOW = 300 * SECOND;
    private final JdbcTemplate jdbc;
    private final TenantSqlScope scope;
    private final TenantApplications applications;
    private final OrganizationService organizations;
    private final LokiClient client;
    private final LokiConnections connections;
    private final ExternalBatchIngestion ingestion;

    record Status(boolean available, String labelName, String labelValue, boolean enabled,
                  OffsetDateTime lastAttemptAt, OffsetDateTime lastSuccessAt, String lastError,
                  long importedCount, long duplicateCount, boolean canReplay) {}

    record Poll(URI endpoint, Binding binding, String label, String value, long cursor, long revision) {
        UUID org() { return binding.organizationId(); }
        long application() { return binding.applicationId(); }
    }

    @Transactional(readOnly = true)
    public Status get(UUID org, long actor, long app) {
        applications.get(org, actor, app);
        scope.enter(org);
        return status(org, app);
    }

    @Transactional
    public Status save(UUID org, long actor, long app, String name, String value, boolean enabled) {
        requireManager(org, actor, app);
        scope.enter(org);
        jdbc.update("""
                insert into public.loki_log_sources as current_source
                    (organization_id,application_id,label_name,label_value,enabled,cursor_ns)
                values (?,?,?,?,?,?)
                on conflict (organization_id, application_id) do update set
                    label_name=excluded.label_name,label_value=excluded.label_value,enabled=excluded.enabled,
                    cursor_ns=case when (current_source.label_name,current_source.label_value)
                                is distinct from (excluded.label_name,excluded.label_value)
                                then excluded.cursor_ns else current_source.cursor_ns end,
                    last_import_start_ns=case when (current_source.label_name,current_source.label_value)
                                is distinct from (excluded.label_name,excluded.label_value)
                                then null else current_source.last_import_start_ns end,
                    last_success_at=case when (current_source.label_name,current_source.label_value)
                                is distinct from (excluded.label_name,excluded.label_value)
                                then null else current_source.last_success_at end,
                    last_error=null,next_poll_at=now(),revision=current_source.revision+1
                """, org, app, name, value, enabled, Math.max(0, nowNanos() - WINDOW));
        return status(org, app);
    }

    @Transactional
    public Status replay(UUID org, long actor, long app) {
        requireManager(org, actor, app);
        scope.enter(org);
        int changed = jdbc.update("""
                update public.loki_log_sources set cursor_ns=least(cursor_ns,last_import_start_ns),
                    next_poll_at=now(),revision=revision+1,last_error=null
                where organization_id=? and application_id=? and enabled and last_import_start_ns is not null
                """, org, app);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "No imported Loki window to replay");
        return status(org, app);
    }

    private void requireManager(UUID org, long actor, long app) {
        if (!organizations.get(org, actor).role().managesOrganization()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient log source permissions");
        }
        applications.get(org, actor, app);
        scope.enter(org);
        if (connections.current(org) == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configure an approved Loki endpoint first");
        }
    }

    private Status status(UUID org, long app) {
        boolean available = connections.current(org) != null;
        return jdbc.query("""
                select * from public.loki_log_sources where organization_id=? and application_id=?
                """, (row, index) -> new Status(available, row.getString("label_name"), row.getString("label_value"),
                row.getBoolean("enabled"), row.getObject("last_attempt_at", OffsetDateTime.class),
                row.getObject("last_success_at", OffsetDateTime.class), row.getString("last_error"),
                row.getLong("imported_count"), row.getLong("duplicate_count"),
                available && row.getBoolean("enabled") && row.getObject("last_import_start_ns") != null),
                org, app).stream().findFirst().orElse(new Status(available, null, null, false,
                null, null, null, 0, 0, false));
    }

    @Transactional(readOnly = true)
    public Poll due(UUID org) {
        scope.enter(org);
        return jdbc.query("""
                select source.*, app.application_name, app.environment, connection.origin
                from public.loki_log_sources source
                join public.application_integrations app
                    on app.organization_id=source.organization_id and app.id=source.application_id
                join public.organizations org on org.id=source.organization_id
                left join public.loki_organization_connections connection on connection.organization_id=source.organization_id
                where source.organization_id=? and source.enabled and source.next_poll_at <= now()
                    and app.deactivated_at is null and org.deactivated_at is null
                order by source.next_poll_at, source.application_id limit 1
                """, (row, index) -> new Poll(client.effectiveEndpoint(org, row.getString("origin")),
                new Binding(org, row.getLong("application_id"),
                row.getString("application_name"), Environment.valueOf(row.getString("environment"))),
                row.getString("label_name"), row.getString("label_value"), row.getLong("cursor_ns"),
                row.getLong("revision")), org).stream().filter(poll -> poll.endpoint() != null).findFirst().orElse(null);
    }

    @Transactional
    public void importWindow(Poll poll, long end, boolean backlog, java.util.List<LokiClient.Line> lines) {
        scope.enter(poll.org());
        if (jdbc.query("""
                select id from public.organizations where id=? and deactivated_at is null for share
                """, (row, index) -> row.getObject(1, UUID.class), poll.org()).isEmpty()) return;
        var active = jdbc.query("""
                select source.application_id
                from public.loki_log_sources source
                join public.application_integrations app
                    on app.organization_id=source.organization_id and app.id=source.application_id
                join public.organizations org on org.id=source.organization_id
                where source.organization_id=? and source.application_id=? and source.revision=?
                    and source.enabled and app.environment=? and app.deactivated_at is null
                    and org.deactivated_at is null
                for update of source for share of app
                """, (row, index) -> row.getLong(1), poll.org(), poll.application(), poll.revision(),
                poll.binding().environment().name());
        if (active.isEmpty()) return;

        var events = client.events(poll.endpoint(), lines, poll.org(), poll.application());
        long imported = 0;
        if (!events.isEmpty()) {
            ingestion.charge(poll.binding(), events.size());
            imported = ingestion.consume(poll.binding(), events).events().stream()
                    .filter(ack -> ack.status() == ExternalBatchIngestion.Status.ACCEPTED).count();
        }
        jdbc.update("""
                update public.loki_log_sources set cursor_ns=?,
                    last_import_start_ns=case when ?>0 then ? else last_import_start_ns end,
                    imported_count=imported_count+?,duplicate_count=duplicate_count+?,
                    last_attempt_at=now(),last_success_at=now(),last_error=null,
                    next_poll_at=now()+?*interval '1 second',revision=revision+1
                where organization_id=? and application_id=?
                """, end, imported, poll.cursor(), imported, events.size() - imported,
                backlog ? 1 : 10, poll.org(), poll.application());
    }

    @Transactional
    public void fail(Poll poll, String error) {
        scope.enter(poll.org());
        jdbc.update("""
                update public.loki_log_sources set last_attempt_at=now(),last_error=?,
                    next_poll_at=now()+interval '30 seconds',revision=revision+1
                where organization_id=? and application_id=? and revision=?
                """, error, poll.org(), poll.application(), poll.revision());
    }

    static long nowNanos() {
        Instant now = Instant.now();
        return now.getEpochSecond() * SECOND + now.getNano();
    }
}
