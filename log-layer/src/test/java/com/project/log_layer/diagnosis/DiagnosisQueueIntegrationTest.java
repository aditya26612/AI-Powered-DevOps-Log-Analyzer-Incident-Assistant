package com.project.log_layer.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import com.project.log_layer.tenant.OrganizationRole;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantApplications;
import com.project.log_layer.tenant.TenantSqlScope;
import com.project.log_layer.tenant.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.project.log_layer.enums.TechStack.PYTHON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:postgresql://127.0.0.1:${DEVINSIGHT_FIXTURE_DB_PORT:55438}/log_layer_db",
        "spring.datasource.username=devinsight_log_app", "spring.datasource.password=${DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate", "diagnosis.enabled=false", "diagnosis.lease=2s",
        "diagnosis.heartbeat=300ms", "diagnosis.attempts=2", "diagnosis.initial-delay=100ms",
        "diagnosis.max-delay=200ms", "notification.auto-diagnose-anomalies=true"})
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE07_FIXTURE", matches = "true")
class DiagnosisQueueIntegrationTest {
    static final LlmAnalysisResponse RESULT = new LlmAnalysisResponse("Test result", "Fixture cause", "HIGH", "Inspect service");
    @Autowired DiagnosisQueue queue;
    @Autowired DiagnosisContext context;
    @Autowired DiagnosisApi.Access access;
    @Autowired ExternalBatchIngestion ingestion;
    @Autowired OrganizationService organizations;
    @Autowired TenantApplications applications;
    @Autowired TenantSqlScope scope;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean MlInferenceClient ml;
    @MockitoBean UserDirectory directory;

    @BeforeEach
    void scoring() {
        when(ml.predict(any())).thenReturn(new MlPredictionResponse(1,"Normal",false,0.02,"2.0.0-candidate"));
    }

    @Test
    void claimsRecoverAfterExpiryAndFenceThePreviousWorker() throws Exception {
        var app = app(7301);
        submit(app, Instant.now(), "queue fence", null);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = workers.submit(() -> queue.claim(app.organizationId(), UUID.randomUUID()));
            var two = workers.submit(() -> queue.claim(app.organizationId(), UUID.randomUUID()));
            var a = one.get(5, TimeUnit.SECONDS);
            var b = two.get(5, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null));
            var original = a == null ? b : a;
            assertTrue(queue.heartbeat(original));
            Thread.sleep(2200);
            assertTrue(access.read(app.organizationId(),7301,original.incident(),null,0).leaseExpired());
            var replacement = queue.claim(app.organizationId(), UUID.randomUUID());
            assertNotNull(replacement);
            assertEquals(original.job(), replacement.job());
            assertEquals(2, replacement.attempt());
            assertFalse(queue.heartbeat(original));
            assertFalse(queue.succeed(original, RESULT));
            assertTrue(queue.succeed(replacement, RESULT));
            assertFalse(queue.succeed(replacement, RESULT));
            var status = access.read(app.organizationId(),7301,replacement.incident(),null,0);
            assertEquals("SUCCEEDED",status.status());
            assertEquals("Fixture cause",status.rootCause());
            assertEquals("Inspect service",status.recommendation());
            assertNotNull(status.completedAt());
            assertEquals("LEASE_EXPIRED",status.attempts().get(1).outcome());
            assertEquals(0, jdbc.queryForObject("select count(*) from public.diagnosis_jobs", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from public.diagnosis_attempts", Integer.class));
        }
    }

    @Test
    void rollingContextUsesOnlySameTenantAndFailureRetriesKeepAttemptHistory() throws Exception {
        var app = app(7302);
        var foreign = app(7303);
        Instant times = Instant.now().minusSeconds(350);
        String trace = "00-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01";
        submit(app, times, "Controlled primary marker", trace);
        submit(app, times.plusSeconds(200), "Controlled supporting marker", trace);
        submit(foreign, times.plusSeconds(200), "Separate tenant marker", trace);
        var claim = queue.claim(app.organizationId(),UUID.randomUUID());
        assertNotNull(claim);
        var work = context.load(claim);
        assertTrue(work.request().message().contains("Controlled supporting marker"));
        assertFalse(work.request().message().contains("Separate tenant marker"));
        assertTrue(queue.fail(claim, DiagnosisQueue.Failure.TRANSPORT, true));
        assertEquals("RETRY_WAIT", access.read(app.organizationId(),7302,claim.incident(),null,0).status());
        Thread.sleep(250);
        var retry = queue.claim(app.organizationId(),UUID.randomUUID());
        assertEquals(claim.job(), retry.job());
        assertTrue(queue.fail(retry, DiagnosisQueue.Failure.UPSTREAM, true));
        assertEquals("FAILED_TERMINAL", access.read(app.organizationId(),7302,claim.incident(),null,0).status());
        organizations.setMember(app.organizationId(),7302,7399,OrganizationRole.VIEWER);
        assertEquals("FAILED_TERMINAL",access.read(app.organizationId(),7399,claim.incident(),null,0).status());
        var denied = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> access.retry(app.organizationId(),7399,claim.incident(),null));
        assertEquals(403,denied.getStatusCode().value());
        var manual = access.retry(app.organizationId(),7302,claim.incident(),null);
        assertEquals("PENDING",manual.status());
        assertEquals(2,manual.attempts().size());
        assertEquals(1,manual.manualRetries());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> access.read(foreign.organizationId(),7303,claim.incident(),null,0));
    }

    @Test
    void sourceIncidentAndJobRollBackTogetherAndLateLogGetsStandaloneWork() throws Exception {
        var app = app(7304);
        byte[] group = payload(app, Instant.now(), "Rollback test", null);
        transactions.executeWithoutResult(tx -> {
            ingestion.submit(group,app);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.diagnosis_jobs",Integer.class));
            tx.setRollbackOnly();
        });
        transactions.executeWithoutResult(tx -> {
            scope.enter(app.organizationId());
            assertEquals(0,jdbc.queryForObject("select count(*) from public.logs",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from public.incidents",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from public.diagnosis_jobs",Integer.class));
        });
        submit(app,Instant.now().minusSeconds(7200),"Old but eligible",null);
        var claim = queue.claim(app.organizationId(),UUID.randomUUID());
        assertNotNull(claim);
        assertNull(claim.incident());
        assertTrue(queue.succeed(claim,RESULT));
    }

    @Test
    void bridgeChoosesEarliestGroupWithoutMergingAndThirtyMinuteCapSplits() throws Exception {
        var app = app(7305);
        Instant start = Instant.now().minusSeconds(2200);
        submit(app,start,"Bridge test",null);
        submit(app,start.plusSeconds(540),"Bridge test",null);
        submit(app,start.plusSeconds(270),"Bridge test",null);
        var counts = transactions.execute(tx -> {
            scope.enter(app.organizationId());
            return jdbc.queryForList("select event_count from public.incidents order by first_event_at", Long.class);
        });
        assertEquals(java.util.List.of(2L,1L),counts);
        for (int i=0; i<9; i++) {
            submit(app,start.plusSeconds(i*240L),"Maximum span test",null);
        }
        var capped = transactions.execute(tx -> {
            scope.enter(app.organizationId());
            return jdbc.queryForObject("select count(*) from public.incidents",Integer.class);
        });
        assertEquals(4,capped);
    }

    @Test
    void threeArrivalOrdersConvergeWithinOneRollingSession() throws Exception {
        Instant first = Instant.now().minusSeconds(500).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        long actor = 7310;
        for (int[] order : new int[][]{{0,1,2},{2,1,0},{1,0,2}}) {
            var app = app(actor++);
            for (int part : order) {
                submit(app,first.plusSeconds(part * 120L),"Single rolling session",null);
            }
            transactions.executeWithoutResult(tx -> {
                scope.enter(app.organizationId());
                var bounds = jdbc.queryForMap("""
                        select count(*) as groups, sum(event_count) as events, min(first_event_at) as first,
                            max(last_event_at) as last from public.incidents
                        """);
                assertEquals(1L,bounds.get("groups"));
                assertEquals(3L,((Number) bounds.get("events")).longValue());
                assertEquals(first,((java.sql.Timestamp) bounds.get("first")).toLocalDateTime()
                        .toInstant(java.time.ZoneOffset.UTC));
                assertEquals(first.plusSeconds(240),((java.sql.Timestamp) bounds.get("last")).toLocalDateTime()
                        .toInstant(java.time.ZoneOffset.UTC));
            });
        }
    }

    private Binding app(long actor) {
        UUID org = organizations.create(actor,"Stage 07 fixture","s07-"+UUID.randomUUID()).id();
        long id = Long.parseLong(applications.create(org,actor,"fixture@example.invalid","payment-service",
                PYTHON,Environment.DEVELOPMENT,null).id());
        return new Binding(org,id,"payment-service",Environment.DEVELOPMENT);
    }

    private void submit(Binding app, Instant time, String message, String trace) throws Exception {
        ingestion.submit(payload(app,time,message,trace),app);
    }

    private byte[] payload(Binding app,Instant time,String message,String trace) throws Exception {
        var body = json.createObjectNode().put("applicationName",app.applicationName()).put("environment","DEVELOPMENT");
        var event = body.putArray("events").addObject().put("eventId",UUID.randomUUID().toString())
                .put("timestamp",time.toString()).put("level","ERROR").put("source","PYTHON").put("message",message);
        if (trace != null) event.put("traceparent",trace).put("traceOrigin","INBOUND_REPORTED");
        return json.writeValueAsBytes(body);
    }
}
