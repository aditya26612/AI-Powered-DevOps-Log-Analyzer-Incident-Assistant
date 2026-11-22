package com.project.log_layer.observability;

import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantSqlScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ObservabilityQueries {
    private static final String V2 = "l.analysis_status='COMPLETED' and l.model_version='2.0.0-candidate'";
    private static final Pattern SECRET = Pattern.compile(
            "(?i)https://hooks\\.slack\\.com/services/[^\\s\"'<>]+"
            + "|\\bdvi_[a-z0-9_-]+|\\bxox[a-z]-[a-z0-9-]+|\\bBearer[ \\t]+\\S+"
            + "|\\b(?:password|passwd|token|secret|[a-z0-9_-]*(?:api[-_ ]?key|secret|password|token))"
            + "[ \\t]*[:=][ \\t]*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|\\S+)");
    private static final String LOG_RANGE = """
            l.organization_id=:org and l.timestamp>=:from and l.timestamp<:to
            """;
    private static final String INCIDENT_APP = """
            and exists (select 1 from public.incident_memberships m join public.logs l
                on l.organization_id=m.organization_id and l.id=m.log_id
                where m.organization_id=i.organization_id and m.incident_id=i.id and l.application_id=:app)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantSqlScope scope;
    private final OrganizationService organizations;

    public enum Range {
        HOUR(Duration.ofHours(1),Duration.ofMinutes(5)),
        DAY(Duration.ofDays(1),Duration.ofHours(1)),
        WEEK(Duration.ofDays(7),Duration.ofHours(6)),
        MONTH(Duration.ofDays(30),Duration.ofDays(1));

        final Duration period;
        final Duration bucket;

        Range(Duration period, Duration bucket) {
            this.period = period;
            this.bucket = bucket;
        }
    }

    public enum Classification { V2_ANOMALY, V2_NORMAL }
    public record Bucket(Instant start, long logs, long anomalies, long errors, long scoringFailures) {}
    public record Incident(UUID id, String severity, String status, String application, long events, Instant first,
                           Instant last, String diagnosisStatus) {}
    public record Activity(String type, Instant time, UUID incidentId) {}
    public record Summary(Instant from, Instant to, long bucketSeconds, List<Bucket> buckets, Map<String,Long> severity,
                          Map<String,Long> incidents, Map<String,Long> diagnoses, Map<String,Long> deliveries,
                          List<Incident> attention, List<Activity> activity) {}
    public record LogFilter(LogLevel level, LogSource source, Environment environment, AnalysisStatus analysisStatus,
                            Classification classification, boolean operationalErrors, UUID incident, UUID correlation, String trace, String search,
                            int page, int size, Instant before) {}
    public record LogRow(String id, Instant occurrence, String application, String environment, String level,
                         String source, String preview, String classification, String analysisStatus, UUID incidentId) {}
    public record LogPage(List<LogRow> content, int page, int size, boolean hasNext, Instant to) {}
    public record Source(String raw, UUID incidentId) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 8)
    public Summary summary(UUID org, long actor, Range range, Long app, Instant now) {
        var params = enter(org,actor,range,app,now).addValue("seconds",range.bucket.toSeconds());
        Instant from = now.minus(range.period);
        long seconds = range.bucket.toSeconds();
        long firstBucket = Math.floorDiv(from.getEpochSecond(),seconds)*seconds;
        Map<Instant,Bucket> timeline = new LinkedHashMap<>();
        for (Instant start = Instant.ofEpochSecond(firstBucket); start.isBefore(now); start = start.plusSeconds(seconds)) {
            timeline.put(start,new Bucket(start,0,0,0,0));
        }
        Map<String,Long> severity = new LinkedHashMap<>();
        jdbc.query("""
                select date_bin(:seconds * interval '1 second',l.timestamp,timestamp '1970-01-01') as bucket,
                       l.level,count(*) as logs,
                       count(*) filter (where %s and l.anomaly) as anomalies,
                       count(*) filter (where l.level in ('ERROR','FATAL')) as errors,
                       count(*) filter (where l.analysis_status='FAILED') as failed
                from public.logs l where
                """.formatted(V2) + LOG_RANGE + appFilter(app,"l") + " group by bucket,l.level order by bucket,l.level",
                params, row -> {
                    Instant start = utc(row.getObject("bucket",LocalDateTime.class));
                    long count = row.getLong("logs");
                    severity.merge(row.getString("level"),count,Long::sum);
                    Bucket previous = timeline.get(start);
                    timeline.put(start,new Bucket(start,previous.logs+count,previous.anomalies+row.getLong("anomalies"),
                            previous.errors+row.getLong("errors"),previous.scoringFailures+row.getLong("failed")));
                });
        String incidentsInRange = """
                from public.incidents i where i.organization_id=:org and i.last_event_at>=:from and i.last_event_at<:to
                """ + (app == null ? "" : INCIDENT_APP);
        var incidents = counts("select i.status,count(*) " + incidentsInRange + " group by i.status",params);
        String jobsInRange = """
                from public.diagnosis_jobs j join public.logs l on l.organization_id=j.organization_id and l.id=j.log_id
                where j.organization_id=:org and j.created_at>=:fromInstant and j.created_at<:toInstant
                """ + appFilter(app,"l");
        var diagnoses = counts("select j.status,count(*) " + jobsInRange + " group by j.status",params);
        var deliveries = counts("""
                select n.status,count(*) from public.notification_outbox n
                where n.organization_id=:org and n.created_at>=:fromInstant and n.created_at<:toInstant
                """ + appFilter(app,"n") + " group by n.status",params);
        var attention = jdbc.query("""
                select i.id,i.severity,i.status,l.application_name,i.event_count,i.first_event_at,i.last_event_at,j.status
                from public.incidents i join public.logs l on l.organization_id=i.organization_id and l.id=i.representative_log_id
                left join public.diagnosis_jobs j on j.organization_id=i.organization_id and j.incident_id=i.id
                where i.organization_id=:org and i.last_event_at>=:from and i.last_event_at<:to and i.status<>'RESOLVED'
                """ + (app == null ? "" : INCIDENT_APP) + " order by i.severity desc,i.last_event_at desc,i.id desc limit 6",
                params,(row,index) -> new Incident(row.getObject(1,UUID.class),switch (row.getInt(2)) {
                    case 3 -> "CRITICAL";
                    case 2 -> "HIGH";
                    default -> "MEDIUM";
                },row.getString(3),row.getString(4),row.getLong(5),utc(row.getObject(6,LocalDateTime.class)),
                        utc(row.getObject(7,LocalDateTime.class)),row.getString(8)));
        var activity = jdbc.query("""
                select event_type,event_time,incident from (
                  select state.kind as event_type,state.at as event_time,i.id as incident
                  from public.incidents i cross join lateral (values ('INCIDENT_CREATED',i.created_at),
                        ('INCIDENT_ACKNOWLEDGED',i.acknowledged_at),('INCIDENT_RESOLVED',i.resolved_at)) state(kind,at)
                  where i.organization_id=:org and state.at>=:fromInstant and state.at<:toInstant
                """ + (app == null ? "" : INCIDENT_APP) + """
                  union all
                  select 'DIAGNOSIS_COMPLETED',j.completed_at,j.incident_id
                  from public.diagnosis_jobs j join public.logs l on l.organization_id=j.organization_id and l.id=j.log_id
                  where j.organization_id=:org and j.status='SUCCEEDED' and j.completed_at>=:fromInstant and j.completed_at<:toInstant
                """ + appFilter(app,"l") + """
                  union all
                  select 'SLACK_ACKNOWLEDGED',n.acknowledged_at,n.incident_id from public.notification_outbox n
                  where n.organization_id=:org and n.status='DELIVERED' and n.acknowledged_at>=:fromInstant
                        and n.acknowledged_at<:toInstant
                """ + appFilter(app,"n") + """
                ) recent order by event_time desc,event_type,incident desc nulls last limit 8
                """,params,(row,index) -> new Activity(row.getString(1),row.getTimestamp(2).toInstant(),
                        row.getObject(3,UUID.class)));
        return new Summary(from,now,seconds,List.copyOf(timeline.values()),severity,incidents,diagnoses,deliveries,
                attention,activity);
    }

    @Transactional(readOnly = true, timeout = 8)
    public LogPage logs(UUID org, long actor, Range range, Long app, Instant now, LogFilter filter) {
        if (filter.page < 0 || filter.page > 200 || filter.size < 1 || filter.size > 50
                || filter.search != null && (filter.search.strip().length() < 2 || filter.search.length() > 100)
                || filter.trace != null && !filter.trace.matches("[0-9a-f]{32}")
                || filter.before != null && (filter.before.isAfter(now) || filter.before.isBefore(now.minus(range.period)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid log filters or page");
        }
        Instant to = filter.before == null ? now : filter.before;
        var params = enter(org,actor,range,app,to)
                .addValue("offset",filter.page * filter.size).addValue("limit",filter.size+1);
        var sql = new StringBuilder("""
                select l.id,l.timestamp,l.application_name,l.environment,l.level,l.source,left(l.message,600),
                       case when %s then case when l.anomaly then 'V2_ANOMALY' else 'V2_NORMAL' end end,
                       l.analysis_status,m.incident_id
                from public.logs l left join public.incident_memberships m
                     on m.organization_id=l.organization_id and m.log_id=l.id
                where
                """.formatted(V2)).append(LOG_RANGE).append(appFilter(app,"l"));
        filter(sql,params,"l.level","level",filter.level);
        filter(sql,params,"l.source","source",filter.source);
        filter(sql,params,"l.environment","environment",filter.environment);
        filter(sql,params,"l.analysis_status","analysisStatus",filter.analysisStatus);
        filter(sql,params,"m.incident_id","incident",filter.incident);
        filter(sql,params,"l.correlation_id","correlation",filter.correlation);
        filter(sql,params,"substring(l.trace_parent,4,32)","trace",filter.trace);
        if (filter.operationalErrors) sql.append(" and l.level in ('ERROR','FATAL')");
        if (filter.classification != null) {
            sql.append(" and ").append(V2).append(" and l.anomaly=:anomaly");
            params.addValue("anomaly",filter.classification==Classification.V2_ANOMALY);
        }
        if (filter.search != null) {
            sql.append(" and strpos(lower(l.message),lower(:search))>0");
            params.addValue("search",filter.search.strip());
        }
        sql.append(" order by l.timestamp desc,l.id desc limit :limit offset :offset");
        var rows = jdbc.query(sql.toString(),params,(row,index) -> new LogRow(row.getString(1),
                utc(row.getObject(2,LocalDateTime.class)),row.getString(3),row.getString(4),row.getString(5),
                row.getString(6),preview(row.getString(7)),row.getString(8),row.getString(9),row.getObject(10,UUID.class)));
        return new LogPage(List.copyOf(rows.subList(0,Math.min(rows.size(),filter.size))),filter.page,filter.size,
                rows.size()>filter.size && filter.page<200,to);
    }

    @Transactional(readOnly = true, timeout = 8)
    public Source source(UUID org, long actor, long log) {
        organizations.get(org,actor);
        scope.enter(org);
        return jdbc.query("""
                select l.raw_log,m.incident_id from public.logs l left join public.incident_memberships m
                     on m.organization_id=l.organization_id and m.log_id=l.id
                where l.organization_id=:org and l.id=:log
                """, new MapSqlParameterSource("org",org).addValue("log",log),
                (row,index) -> new Source(redact(row.getString(1)),row.getObject(2,UUID.class)))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Log not found"));
    }

    private static String redact(String value) {
        return value == null ? null : SECRET.matcher(value).replaceAll("[redacted]");
    }

    private static String preview(String value) {
        String visible = redact(value);
        if (visible == null || visible.codePointCount(0,visible.length()) <= 240) {
            return visible;
        }
        return visible.substring(0,visible.offsetByCodePoints(0,239)) + "…";
    }

    private MapSqlParameterSource enter(UUID org, long actor, Range range, Long app, Instant to) {
        organizations.get(org,actor);
        scope.enter(org);
        if (app != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from public.application_integrations where organization_id=:org and id=:app)
                """,new MapSqlParameterSource("org",org).addValue("app",app),Boolean.class))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application not found");
        }
        Instant from = to.minus(range.period);
        return new MapSqlParameterSource("org",org).addValue("app",app)
                .addValue("from",LocalDateTime.ofInstant(from,ZoneOffset.UTC))
                .addValue("to",LocalDateTime.ofInstant(to,ZoneOffset.UTC))
                .addValue("fromInstant",from.atOffset(ZoneOffset.UTC))
                .addValue("toInstant",to.atOffset(ZoneOffset.UTC));
    }

    private Map<String,Long> counts(String sql, MapSqlParameterSource params) {
        Map<String,Long> result = new LinkedHashMap<>();
        jdbc.query(sql,params,row -> { result.put(row.getString(1),row.getLong(2)); });
        return result;
    }

    private static String appFilter(Long app, String alias) {
        return app == null ? "" : " and " + alias + ".application_id=:app ";
    }

    private static void filter(StringBuilder sql, MapSqlParameterSource params, String field, String key, Object value) {
        if (value != null) {
            sql.append(" and ").append(field).append("=:").append(key);
            params.addValue(key,value instanceof Enum<?> option ? option.name() : value);
        }
    }

    private static Instant utc(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
