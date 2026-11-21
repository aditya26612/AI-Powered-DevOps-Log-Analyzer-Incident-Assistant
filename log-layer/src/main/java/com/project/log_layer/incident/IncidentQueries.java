package com.project.log_layer.incident;

import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantSqlScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentQueries {
    private static final String FROM = """
            from public.incidents i
            join public.logs r on r.organization_id=i.organization_id and r.id=i.representative_log_id
            left join public.logs d on d.organization_id=i.organization_id and d.id=i.diagnosis_log_id
            where i.organization_id=:organization
            """;
    private static final String SUMMARY_FIELDS = """
            select i.id, i.status, i.severity, i.environment, i.correlation_rule, i.rule_version,
                   i.window_start, i.window_end, i.first_event_at, i.last_event_at, i.event_count, i.version,
                   i.representative_log_id, r.application_name, i.diagnosis_log_id, d.llm_analysis_status,
                   i.acknowledged_by, i.acknowledged_at, i.resolved_by, i.resolved_at
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantSqlScope scope;
    private final OrganizationService organizations;

    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }
    public enum Severity { MEDIUM, HIGH, CRITICAL }
    public record Summary(UUID id, Status status, Severity severity, Environment environment,
                          String correlationRule, int ruleVersion, Instant windowStart, Instant windowEnd,
                          Instant firstEventAt, Instant lastEventAt, long eventCount, int version,
                          String representativeLogId, String representativeApplication,
                          String diagnosisLogId, AnalysisStatus diagnosisStatus,
                          String acknowledgedBy, Instant acknowledgedAt, String resolvedBy, Instant resolvedAt) {}
    public record Member(String logId, Instant timestamp, String application, LogLevel level, String reason, boolean seed) {}
    public record Page<T>(List<T> content, int page, int size, boolean hasNext) {}

    @Transactional(readOnly = true)
    public Page<Summary> list(UUID organization, long actor, Status status, Severity severity, Long applicationId,
                              Instant from, Instant to, int page, int size) {
        enter(organization, actor, false);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From must precede to");
        }
        var sql = new StringBuilder(SUMMARY_FIELDS).append(FROM);
        var parameters = pagination(organization, page, size);
        if (status != null) {
            sql.append(" and i.status=:status");
            parameters.addValue("status", status.name());
        }
        if (severity != null) {
            sql.append(" and i.severity=:severity");
            parameters.addValue("severity", severity.ordinal() + 1);
        }
        if (from != null) {
            sql.append(" and i.last_event_at>=:from");
            parameters.addValue("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            sql.append(" and i.last_event_at<:to");
            parameters.addValue("to", LocalDateTime.ofInstant(to, ZoneOffset.UTC));
        }
        if (applicationId != null) {
            sql.append("""
                     and exists (select 1 from public.incident_memberships m
                        join public.logs l on l.organization_id=m.organization_id and l.id=m.log_id
                        where m.organization_id=i.organization_id and m.incident_id=i.id and l.application_id=:application)
                    """);
            parameters.addValue("application", applicationId);
        }
        sql.append(" order by i.last_event_at desc, i.id desc limit :limit offset :offset");
        return readPage(sql.toString(), parameters, IncidentQueries::summary, page, size);
    }

    @Transactional(readOnly = true)
    public Summary detail(UUID organization, long actor, UUID incident) {
        enter(organization, actor, false);
        return find(organization, incident);
    }

    @Transactional(readOnly = true)
    public Page<Member> members(UUID organization, long actor, UUID incident, int page, int size) {
        enter(organization, actor, false);
        find(organization, incident);
        var parameters = pagination(organization, page, size).addValue("incident", incident);
        return readPage("""
                select m.log_id, l.timestamp, l.application_name, l.level, m.reason, m.seed
                from public.incident_memberships m join public.logs l
                    on l.organization_id=m.organization_id and l.id=m.log_id
                where m.organization_id=:organization and m.incident_id=:incident
                order by m.log_id limit :limit offset :offset
                """, parameters, (rs, row) -> new Member(rs.getString("log_id"), time(rs, "timestamp"),
                rs.getString("application_name"), LogLevel.valueOf(rs.getString("level")), rs.getString("reason"),
                rs.getBoolean("seed")), page, size);
    }

    @Transactional
    public Summary transition(UUID organization, long actor, UUID incident, int version, Status target) {
        enter(organization, actor, true);
        String update = switch (target) {
            case ACKNOWLEDGED -> """
                    status='ACKNOWLEDGED', acknowledged_by=:actor, acknowledged_at=now(),
                    version=version+1, updated_at=now()
                    where organization_id=:organization and id=:incident and version=:version and status='OPEN'
                    """;
            case RESOLVED -> """
                    status='RESOLVED', resolved_by=:actor, resolved_at=now(),
                    version=version+1, updated_at=now()
                    where organization_id=:organization and id=:incident and version=:version and status<>'RESOLVED'
                    """;
            case OPEN -> throw new IllegalArgumentException("OPEN is assigned by correlation");
        };
        var args = parameters(organization, incident).addValue("actor", actor).addValue("version", version);
        if (jdbc.update("update public.incidents set " + update, args) != 1) {
            find(organization, incident);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Incident state changed or transition is unavailable");
        }
        return find(organization, incident);
    }

    private void enter(UUID organization, long actor, boolean write) {
        var membership = organizations.get(organization, actor);
        if (write && !membership.role().managesApplications()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient incident permissions");
        }
        scope.enter(organization);
    }

    private Summary find(UUID organization, UUID incident) {
        return jdbc.query(SUMMARY_FIELDS + FROM + " and i.id=:incident", parameters(organization, incident),
                IncidentQueries::summary).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    private <T> Page<T> readPage(String sql, MapSqlParameterSource parameters, RowMapper<T> mapper, int page, int size) {
        var rows = jdbc.query(sql, parameters, mapper);
        return new Page<>(List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
    }

    private static MapSqlParameterSource parameters(UUID organization, UUID incident) {
        return new MapSqlParameterSource("organization", organization).addValue("incident", incident);
    }

    private static MapSqlParameterSource pagination(UUID organization, int page, int size) {
        if (page < 0 || page > 500 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid incident pagination");
        }
        return new MapSqlParameterSource("organization", organization)
                .addValue("limit", size + 1).addValue("offset", page * size);
    }

    private static Summary summary(ResultSet row, int index) throws SQLException {
        String diagnosisStatus = row.getString("llm_analysis_status");
        return new Summary(row.getObject("id", UUID.class), Status.valueOf(row.getString("status")),
                Severity.values()[row.getInt("severity") - 1], Environment.valueOf(row.getString("environment")),
                row.getString("correlation_rule"), row.getInt("rule_version"), time(row, "window_start"),
                time(row, "window_end"), time(row, "first_event_at"), time(row, "last_event_at"),
                row.getLong("event_count"), row.getInt("version"), row.getString("representative_log_id"),
                row.getString("application_name"), row.getString("diagnosis_log_id"),
                diagnosisStatus == null ? null : AnalysisStatus.valueOf(diagnosisStatus),
                row.getString("acknowledged_by"), auditTime(row, "acknowledged_at"),
                row.getString("resolved_by"), auditTime(row, "resolved_at"));
    }

    private static Instant time(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private static Instant auditTime(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
