package com.project.log_layer.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.entity.Log;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.observability.ObservabilityQueries;
import com.project.log_layer.security.SignedIdentity;
import com.project.log_layer.tenant.ApplicationCredentials;
import com.project.log_layer.tenant.OrganizationRole;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantApplications;
import com.project.log_layer.tenant.TenantSqlScope;
import com.project.log_layer.tenant.UserDirectory;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:${DEVINSIGHT_FIXTURE_DB_PORT:55438}/log_layer_db",
        "spring.datasource.username=devinsight_log_app",
        "spring.datasource.password=${DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "notification.auto-diagnose-anomalies=true",
        "notification.slack.enabled=false",
        "notification.email.enabled=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE06_FIXTURE", matches = "true")
class IncidentIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired TenantSqlScope scope;
    @Autowired IncidentPolicy policy;
    @Autowired ObservabilityQueries observability;
    @Autowired OrganizationService organizations;
    @Autowired TenantApplications applications;
    @Autowired ApplicationCredentials credentials;
    @Value("${JWT_SECRET}") String signingSecret;
    @MockitoBean MlInferenceClient ml;
    @MockitoBean UserDirectory directory;

    @BeforeEach
    void configureScorers() {
        when(ml.predict(any())).thenReturn(new MlPredictionResponse(1, "Normal", false, 0.02, "2.0.0-candidate"));
    }

    @Test
    void dashboardCountsDistinctTenantEventsAndUsesHalfOpenUtcBuckets() throws Exception {
        var owner=organization(6290);
        var other=organization(6291);
        var source=app(owner,"shared-name");
        var foreign=app(other,"shared-name");
        Instant to=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Instant from=to.minusSeconds(3600);
        send(source,from.minusMillis(1),"INFO","Outside dashboard range",null);
        send(source,from,"ERROR","Window boundary",null);
        var event=payload(source,UUID.randomUUID(),from.plusSeconds(300),"INFO","Exact next bucket",null);
        when(ml.predict(any())).thenReturn(new MlPredictionResponse(-1,"Anomaly",true,-0.05,"2.0.0-candidate"));
        request(source,event,200);
        request(source,event,200);
        send(source,to,"INFO","Exclusive upper bound",null);
        send(foreign,from.plusSeconds(300),"INFO","Same time and app name",null);
        var summary=observability.summary(owner.id(),owner.actor(),ObservabilityQueries.Range.HOUR,null,to);
        assertEquals(12,summary.buckets().size());
        assertEquals(2,summary.buckets().stream().mapToLong(ObservabilityQueries.Bucket::logs).sum());
        assertEquals(1,summary.buckets().get(0).errors());
        assertEquals(1,summary.buckets().get(1).anomalies());
        assertEquals(1,summary.severity().get("ERROR"));
        assertEquals(1,summary.severity().get("INFO"));
    }

    @Test
    void observabilityHttpChecksMembershipAndUsesBoundedStablePages() throws Exception {
        var owner=organization(6292);
        var other=organization(6293);
        var source=app(owner,"shared-name");
        var foreign=app(other,"shared-name");
        Instant time=Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String first=send(source,time,"INFO","Memory % owned",null);
        String second=send(source,time,"INFO","Memory % present",null);
        send(foreign,time,"ERROR","Memory % foreign",null);
        String path="/api/v1/organizations/" + owner.id() + "/observability";
        var result=json.readTree(http.perform(post(path+"/logs").header("Authorization",token(owner.actor()))
                        .param("size","1").contentType("application/json").content("{\"text\":\"Memory %\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.content[0].id").value(second))
                .andReturn().getResponse().getContentAsString());
        http.perform(post(path+"/logs").header("Authorization",token(owner.actor())).param("size","1").param("page","1")
                        .param("before",result.path("to").asText()).contentType("application/json").content("{\"text\":\"Memory %\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.content[0].id").value(first));
        http.perform(get(path+"/summary").header("Authorization",token(other.actor()))).andExpect(status().isNotFound());
        http.perform(get(path+"/summary").header("X-API-Key",source.key())).andExpect(status().isUnauthorized());
        http.perform(get(path+"/summary").header("Authorization",token(owner.actor())).param("range","YEAR"))
                .andExpect(status().isBadRequest());
        http.perform(get(path+"/logs").header("Authorization",token(owner.actor())).param("size","51"))
                .andExpect(status().isBadRequest());
        http.perform(get(path+"/logs").header("Authorization",token(owner.actor())).param("trace","invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void observabilitySeparatesScoringFailuresAndMasksSourceCredentials() throws Exception {
        var owner=organization(6294);
        var foreign=organization(6295);
        var source=app(owner,"checkout");
        when(ml.predict(any())).thenReturn(null);
        String id=send(source,Instant.now().minusSeconds(10),"ERROR",
                "checkout failed\nDB_PASSWORD=synthetic_password_marker",null);
        String path="/api/v1/organizations/" + owner.id() + "/observability";
        http.perform(get(path+"/logs").header("Authorization",token(owner.actor())).param("analysisStatus","FAILED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].classification").isEmpty())
                .andExpect(jsonPath("$.content[0].preview",org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("synthetic_password_marker"))));
        http.perform(get(path+"/logs").header("Authorization",token(owner.actor())).param("classification","V2_NORMAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        http.perform(get(path+"/logs/"+id+"/source").header("Authorization",token(owner.actor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.raw",org.hamcrest.Matchers.containsString("\n")))
                .andExpect(jsonPath("$.raw",org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("synthetic_password_marker"))));
        http.perform(get("/api/v1/organizations/"+foreign.id()+"/observability/logs/"+id+"/source")
                        .header("Authorization",token(foreign.actor())))
                .andExpect(status().isNotFound());
    }

    @Test
    void traceCorrelationIsTenantScopedWithContextAndRoleProtectedLifecycle() throws Exception {
        var left = organization(6200);
        var right = organization(6201);
        organizations.setMember(left.id(), left.actor(), 6202, OrganizationRole.VIEWER);
        var payments = app(left, "payment-service");
        var gateway = app(left, "api-gateway");
        var otherPayments = app(right, "payment-service");
        UUID trace = UUID.randomUUID();
        Instant window = windowStart();
        String first = send(payments, window.plusSeconds(30), "ERROR", "Payment dependency failed", trace);
        String second = send(gateway, window.plusSeconds(15), "FATAL", "Upstream routing failed", trace);
        String context = send(gateway, window.plusSeconds(17), "INFO", "Recovery response accepted", trace);
        String other = send(otherPayments, window.plusSeconds(30), "ERROR", "Payment dependency failed", trace);

        JsonNode incident = list(left).path("content").get(0);
        String id = incident.path("id").asText();
        assertEquals(3, incident.path("eventCount").asInt());
        assertEquals("CRITICAL", incident.path("severity").asText());
        assertEquals(window.plusSeconds(15), Instant.parse(incident.path("firstEventAt").asText()));
        assertEquals(first, incident.path("diagnosisLogId").asText());
        assertNotEquals(id, list(right).path("content").get(0).path("id").asText());
        var members = json.readTree(http.perform(get(left.path() + "/" + id + "/logs")
                .header("Authorization", token(left.actor()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("content");
        assertEquals(3, members.size());
        assertTrue(members.toString().contains("SAME_SUPPLIED_TRACE"));
        assertTrue(members.toString().contains("TRACE_CONTEXT"));
        assertTrue(members.toString().contains(second));
        assertTrue(members.toString().contains(context));

        http.perform(get(right.path() + "/" + id).header("Authorization", token(right.actor())))
                .andExpect(status().isNotFound());
        http.perform(get(left.path()).header("X-API-Key", payments.key())).andExpect(status().isUnauthorized());
        http.perform(post(left.path() + "/" + id + "/resolve").header("Authorization", token(6202))
                .contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
        transition(left, id, "acknowledge", 0, 200);
        transition(left, id, "resolve", 0, 409);
        transition(left, id, "resolve", 1, 200);
        String recurrence = send(payments, window.plusSeconds(40), "ERROR", "Payment dependency failed", trace);
        assertNotEquals(first, recurrence);
        assertEquals(2, list(left).path("content").size());
        assertEquals(2, jobs(left.id()));
        assertEquals(1, jobs(right.id()));

        UUID incidentId = UUID.fromString(id);
        assertThrows(DataIntegrityViolationException.class, () -> transactions.executeWithoutResult(transaction -> {
            scope.enter(right.id());
            jdbc.update("""
                    insert into public.incident_memberships (organization_id,incident_id,log_id,reason,seed)
                    values (?, ?, ?, 'INITIAL_SEED', true)
                    """, right.id(), incidentId, Long.parseLong(other));
        }));
        // The failed transaction must leave the connection without tenant visibility.
        assertEquals(0, jdbc.queryForObject("select count(*) from public.incidents", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from public.incident_memberships", Integer.class));
        transactions.executeWithoutResult(transaction -> {
            jdbc.queryForObject("select set_config('devinsight.organization_id','invalid',true)", String.class);
            assertEquals(0, jdbc.queryForObject("select count(*) from public.incidents", Integer.class));
        });
        assertFalse(jdbc.queryForObject("select has_table_privilege(current_user,'public.incidents','DELETE')", Boolean.class));
    }

    @Test
    void fallbackSignaturesDeduplicateGroupingButKeepLogsAndExcludeVeryLateEvents() throws Exception {
        var owner = organization(6210);
        var application = app(owner, "payment-service");
        Instant window = windowStart();
        String first = send(application, window.plusSeconds(20), "ERROR", "HTTP 503 request-id=a1 /checkout", null);
        String second = send(application, window.plusSeconds(10), "ERROR", "HTTP 503 request-id=b2 /checkout", null);
        send(application, window.plusSeconds(11), "ERROR", "HTTP 401 request-id=b2 /checkout", null);
        send(application, window.minusSeconds(3600), "ERROR", "HTTP 503 request-id=a1 /checkout", null);
        assertNotEquals(first, second);
        var groups = list(owner).path("content");
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).path("eventCount").asInt());
        assertEquals("APPLICATION_SIGNATURE", groups.get(0).path("correlationRule").asText());
    }

    @Test
    void contendedGroupRollsBackSourceLogAndRetriedEventsShareOneIncident() throws Exception {
        var owner = organization(6220);
        var first = app(owner, "payment-service");
        var second = app(owner, "api-gateway");
        Instant time = windowStart().plusSeconds(10);
        UUID trace = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var sample = Log.builder().organizationId(owner.id()).applicationId(1L).externalEventId(eventId)
                .environment(Environment.DEVELOPMENT).correlationOrigin(Log.CorrelationOrigin.COLLECTOR_SUPPLIED)
                .correlationId(trace).timestamp(LocalDateTime.ofInstant(time, ZoneOffset.UTC))
                .level(LogLevel.ERROR).message("Controlled failure").build();
        var candidate = policy.candidate(sample, Instant.now()).orElseThrow();
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder = threads.submit(() -> transactions.executeWithoutResult(transaction -> {
                assertTrue(jdbc.queryForObject("select pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                        Boolean.class, candidate.lockKey(owner.id())));
                acquired.countDown();
                await(release);
            }));
            var firstPayload = payload(first, eventId, time, "ERROR", "Controlled failure", trace);
            try {
                await(acquired);
                request(first, firstPayload, 503);
                var count = transactions.execute(transaction -> {
                    scope.enter(owner.id());
                    return jdbc.queryForObject("select count(*) from public.logs where external_event_id=?",
                            Integer.class, eventId);
                });
                assertEquals(0, count);
            } finally {
                release.countDown();
            }
            holder.get(8, TimeUnit.SECONDS);

            var start = new CountDownLatch(1);
            var another = payload(second, UUID.randomUUID(), time, "ERROR", "Related failure", trace);
            var taskA = threads.submit(() -> { await(start); retry(first, firstPayload); return null; });
            var taskB = threads.submit(() -> { await(start); retry(second, another); return null; });
            start.countDown();
            taskA.get(12, TimeUnit.SECONDS);
            taskB.get(12, TimeUnit.SECONDS);
        }
        var groups = list(owner).path("content");
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).path("eventCount").asInt());
        assertEquals(1, jobs(owner.id()));
    }

    private int jobs(UUID organization) {
        return transactions.execute(transaction -> {
            scope.enter(organization);
            return jdbc.queryForObject("select count(*) from public.diagnosis_jobs",Integer.class);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(8, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private void retry(App app, JsonNode body) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            var response = http.perform(post("/api/v1/logs/batch").header("X-API-Key", app.key())
                    .contentType("application/json").content(body.toString())).andReturn().getResponse();
            if (response.getStatus() == 200) {
                return;
            }
            assertEquals(503, response.getStatus());
            Thread.sleep(80);
        }
        fail("Incident correlation did not recover from contention");
    }

    private void transition(Owner owner, String id, String action, int version, int response) throws Exception {
        http.perform(post(owner.path() + "/" + id + "/" + action).header("Authorization", token(owner.actor()))
                .contentType("application/json").content("{\"version\":" + version + "}"))
                .andExpect(status().is(response));
    }

    private JsonNode list(Owner owner) throws Exception {
        return json.readTree(http.perform(get(owner.path()).header("Authorization", token(owner.actor())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String send(App app, Instant time, String severity, String message, UUID trace) throws Exception {
        return request(app, payload(app, UUID.randomUUID(), time, severity, message, trace), 200)
                .path("events").get(0).path("logId").asText();
    }

    private JsonNode payload(App app, UUID id, Instant time, String severity, String message, UUID trace) {
        var body = json.createObjectNode().put("applicationName", app.name()).put("environment", "DEVELOPMENT");
        var event = body.putArray("events").addObject().put("eventId", id.toString())
                .put("timestamp", time.toString()).put("level", severity).put("message", message).put("source", "PYTHON");
        if (trace != null) {
            event.put("correlationId", trace.toString());
        }
        return body;
    }

    private JsonNode request(App app, JsonNode body, int expected) throws Exception {
        return json.readTree(http.perform(post("/api/v1/logs/batch").header("X-API-Key", app.key())
                        .contentType("application/json").content(body.toString())).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    private String token(long actor) {
        return "Bearer " + Jwts.builder().subject("fixture" + actor + "@example.invalid")
                .claim("user_id", actor).claim("role", "DEVELOPER")
                .expiration(Date.from(Instant.now().plusSeconds(180))).signWith(SignedIdentity.signingKey(signingSecret)).compact();
    }

    private Owner organization(long actor) {
        UUID id = organizations.create(actor, "Stage 06 fixture", "s06-" + UUID.randomUUID()).id();
        return new Owner(id, actor);
    }

    private App app(Owner owner, String name) {
        long app = Long.parseLong(applications.create(owner.id(), owner.actor(), "fixture@example.invalid", name,
                com.project.log_layer.enums.TechStack.PYTHON, Environment.DEVELOPMENT, null).id());
        return new App(name, credentials.create(owner.id(), app, "Fixture credential", null).key());
    }

    private static Instant windowStart() {
        long time = Instant.now().getEpochSecond();
        // Use the current window unless an anchor few seconds in its future would exceed the configured skew.
        return Instant.ofEpochSecond(Math.floorDiv(time, 300) * 300);
    }

    private record Owner(UUID id, long actor) {
        String path() { return "/api/v1/organizations/" + id + "/incidents"; }
    }
    private record App(String name, String key) {}
}
