package com.project.log_layer.logsource;

import com.project.log_layer.tenant.OrganizationRole;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantSqlScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class LokiConnections {
    private final JdbcTemplate jdbc;
    private final TenantSqlScope scope;
    private final OrganizationService organizations;
    private final LokiClient client;

    record Settings(String endpoint, boolean canConfigure) {}

    @Transactional(readOnly = true)
    public Settings get(UUID org, long actor) {
        requireOwner(org, actor);
        scope.enter(org);
        return settings(org);
    }

    @Transactional
    public Settings save(UUID org, long actor, String input) {
        // Serialize endpoint edits and checkpoint commits in the same lock order.
        if (jdbc.query("""
                select id from public.organizations where id=? and deactivated_at is null for update
                """, (row, index) -> row.getObject(1, UUID.class), org).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        requireOwner(org, actor);
        URI endpoint = client.approvedEndpoint(org, input);
        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Loki endpoint is not approved for this organization");
        }
        scope.enter(org);
        URI previous = current(org);
        jdbc.update("""
                insert into public.loki_organization_connections (organization_id,origin) values (?,?)
                on conflict (organization_id) do update set origin=excluded.origin
                """, org, endpoint.toString());
        if (!endpoint.equals(previous)) {
            jdbc.update("""
                    update public.loki_log_sources set cursor_ns=?,last_import_start_ns=null,
                        last_attempt_at=null,last_success_at=null,last_error=null,next_poll_at=now(),
                        revision=revision+1
                    where organization_id=?
                    """, Math.max(0, LokiSources.nowNanos() - LokiSources.WINDOW), org);
        }
        return new Settings(endpoint.toString(), true);
    }

    /** Reads an endpoint inside the caller's tenant-scoped transaction. */
    URI current(UUID org) {
        if (!client.canConfigure(org)) return null;
        String saved = jdbc.query("""
                select origin from public.loki_organization_connections where organization_id=?
                """, (row, index) -> row.getString(1), org).stream().findFirst().orElse(null);
        return client.effectiveEndpoint(org, saved);
    }

    private Settings settings(UUID org) {
        URI endpoint = current(org);
        return new Settings(endpoint == null ? null : endpoint.toString(), client.canConfigure(org));
    }

    private void requireOwner(UUID org, long actor) {
        if (organizations.get(org, actor).role() != OrganizationRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the organization owner can manage the Loki endpoint");
        }
    }
}
