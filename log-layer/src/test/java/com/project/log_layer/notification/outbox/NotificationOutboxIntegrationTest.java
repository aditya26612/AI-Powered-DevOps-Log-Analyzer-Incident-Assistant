package com.project.log_layer.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.diagnosis.DiagnosisQueue;
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
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.project.log_layer.enums.TechStack.PYTHON;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:postgresql://127.0.0.1:15438/log_layer_db",
        "spring.datasource.username=devinsight_log_app","spring.datasource.password=${DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate","diagnosis.enabled=false","notification.delivery.transport=FIXTURE",
        "notification.delivery.worker=false","notification.delivery.attempts=2",
        "notification.delivery.lease=5s","notification.delivery.heartbeat=500ms","notification.delivery.http-timeout=2s",
        "notification.auto-diagnose-anomalies=true"})
@ActiveProfiles("stage08-fixture")
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE08_FIXTURE",matches = "true")
class NotificationOutboxIntegrationTest {
    private static final AtomicInteger RECEIVED=new AtomicInteger();
    private static final AtomicReference<String> PAYLOAD=new AtomicReference<>();
    private static final AtomicReference<String> MODE=new AtomicReference<>("ok");
    private static HttpServer receiver;
    private static final LlmAnalysisResponse MODEL = new LlmAnalysisResponse(
            "Inspect <!channel> at https://example.invalid and password=fake-fixture-value",
            "Fixture cause","HIGH","Review dependency health");

    @DynamicPropertySource
    static void receiver(DynamicPropertyRegistry registry) throws IOException {
        receiver=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        receiver.createContext("/notification",exchange -> {
            try(exchange) {
                PAYLOAD.set(new String(exchange.getRequestBody().readNBytes(12_000),StandardCharsets.UTF_8));
                RECEIVED.incrementAndGet();
                String mode=MODE.get();
                if ("lost".equals(mode)) return;
                int status=switch(mode) {
                    case "rate" -> 429;
                    case "busy" -> 503;
                    case "invalid" -> 400;
                    case "forbidden" -> 403;
                    case "rejected" -> 404;
                    case "archived" -> 410;
                    case "redirect" -> 307;
                    default -> 200;
                };
                if (status==429) exchange.getResponseHeaders().set("Retry-After","3");
                if (status==307) exchange.getResponseHeaders().set("Location","/forbidden");
                exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
                byte[] body=("malformed".equals(mode) ? "accepted" : "oversized".equals(mode) ? "x".repeat(2048)
                        : status==200 ? "ok" : "fixture")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status,body.length);
                exchange.getResponseBody().write(body);
            }
        });
        receiver.createContext("/forbidden",exchange -> {
            RECEIVED.addAndGet(100);
            exchange.close();
        });
        receiver.start();
        registry.add("notification.delivery.fixture-url",()->"http://127.0.0.1:" + receiver.getAddress().getPort() + "/notification");
    }

    @AfterAll
    static void stopReceiver() { if(receiver!=null) receiver.stop(0); }

    @Autowired DiagnosisQueue diagnoses;
    @Autowired NotificationQueue queue;
    @Autowired NotificationDeliveryWorker worker;
    @Autowired NotificationApi.Access access;
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
    void setup() {
        MODE.set("ok");
        RECEIVED.set(0);
        PAYLOAD.set(null);
        when(ml.predict(any())).thenReturn(new MlPredictionResponse(1,"Normal",false,0.02,"2.0.0-candidate"));
    }

    @Test
    void diagnosisAndOutboxRollBackTogetherAndAcceptedResultIsUnique() throws Exception {
        var app=application(8801,true);
        var claim=diagnose(app,null);
        transactions.executeWithoutResult(tx -> {
            assertTrue(diagnoses.succeed(claim,MODEL));
            assertEquals(1,count(app.org()));
            tx.setRollbackOnly();
        });
        assertEquals(0,count(app.org()));
        assertTrue(diagnoses.succeed(claim,MODEL));
        assertFalse(diagnoses.succeed(claim,MODEL));
        assertEquals("PENDING",delivery(app,claim).status());
        assertEquals(1,count(app.org()));
    }

    @Test
    void realHttpDeliveryUsesScopedHistoryAndRedactsPayload() throws Exception {
        var app=application(8802,true);
        var claim=complete(app);
        worker.deliver(requireClaim(app));
        var result=delivery(app,claim);
        assertEquals("DELIVERED",result.status());
        assertEquals(1,RECEIVED.get());
        assertNotNull(result.acknowledgedAt());
        assertEquals(200,access.attempts(app.org(),app.actor,result.id(),0).content().getFirst().httpStatus());
        assertTrue(PAYLOAD.get().contains(result.id().toString()));
        assertFalse(PAYLOAD.get().contains("@channel"));
        assertFalse(PAYLOAD.get().contains("fake-fixture-value"));
        assertFalse(PAYLOAD.get().contains("https://example.invalid"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->access.retry(app.org(),app.actor,result.id(),true)).getStatusCode().value());
        var foreign=application(8812,true);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->access.delivery(foreign.org(),foreign.actor,result.id())).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("select count(*) from public.notification_outbox",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from public.notification_attempts",Integer.class));
    }

    @Test
    void localProviderRateLimitUsesCooldownAndExhaustsBoundedRetries() throws Exception {
        var app=application(8803,true);
        var first=complete(app);
        MODE.set("rate");
        worker.deliver(requireClaim(app));
        assertEquals("RETRY_WAIT",delivery(app,first).status());
        assertTrue(delivery(app,first).nextAttemptAt().toInstant().isAfter(Instant.now().plusSeconds(1)));
        assertNull(queue.claim(app.org(),UUID.randomUUID()));
        var unrelated=application(8833,true);
        var unrelatedDiagnosis=complete(unrelated);
        MODE.set("ok");
        worker.deliver(requireClaim(unrelated));
        assertEquals("DELIVERED",delivery(unrelated,unrelatedDiagnosis).status());
        MODE.set("busy");
        var second=await().atMost(Duration.ofSeconds(8)).until(()->queue.claim(app.org(),UUID.randomUUID()),java.util.Objects::nonNull);
        worker.deliver(second);
        assertEquals("FAILED_TERMINAL",delivery(app,first).status());
        assertEquals(3,RECEIVED.get());
        var history=access.attempts(app.org(),app.actor,delivery(app,first).id(),0).content();
        assertEquals(503,history.get(0).httpStatus());
        assertEquals(429,history.get(1).httpStatus());
    }

    @Test
    void temporaryFailureRecoversTheSameDeliveryAndPermanentRejectionStops() throws Exception {
        var app=application(8843,true);
        var diagnosis=complete(app);
        var original=delivery(app,diagnosis).id();
        MODE.set("busy");
        worker.deliver(requireClaim(app));
        assertEquals("RETRY_WAIT",delivery(app,diagnosis).status());
        MODE.set("ok");
        var second=await().atMost(Duration.ofSeconds(8)).until(()->queue.claim(app.org(),UUID.randomUUID()),java.util.Objects::nonNull);
        worker.deliver(second);
        assertEquals(original,delivery(app,diagnosis).id());
        assertEquals("DELIVERED",delivery(app,diagnosis).status());
        var rejected=application(8844,true);
        var rejectedDiagnosis=complete(rejected);
        MODE.set("rejected");
        worker.deliver(requireClaim(rejected));
        var failed=delivery(rejected,rejectedDiagnosis);
        assertEquals("FAILED_TERMINAL",failed.status());
        assertEquals(1,failed.attemptCount());
        assertEquals(404,access.attempts(rejected.org(),rejected.actor,failed.id(),0).content().getFirst().httpStatus());
        assertNull(queue.claim(rejected.org(),UUID.randomUUID()));
    }

    @Test
    void sharedWebhookCoordinatesIndependentAppRegistrationsWithoutLeakingDestinationData() throws Exception {
        var first=application(8853,true);
        var second=application(8854,true);
        String shared=webhook("shared-fixture");
        applications.setWebhook(first.org(),first.actor,first.binding.applicationId(),shared);
        applications.setWebhook(second.org(),second.actor,second.binding.applicationId(),shared);
        var firstDiagnosis=complete(first);
        var secondDiagnosis=complete(second);
        var claimed=requireClaim(first);
        assertNull(queue.claim(second.org(),UUID.randomUUID()));
        assertEquals(0,delivery(second,secondDiagnosis).attemptCount());
        MODE.set("rate");
        worker.deliver(claimed);
        assertNull(queue.claim(second.org(),UUID.randomUUID()));
        assertEquals(1,RECEIVED.get());
        MODE.set("ok");
        var secondClaim=await().atMost(Duration.ofSeconds(8)).until(
                ()->queue.claim(second.org(),UUID.randomUUID()),java.util.Objects::nonNull);
        worker.deliver(secondClaim);
        assertEquals("DELIVERED",delivery(second,secondDiagnosis).status());
        assertEquals("RETRY_WAIT",delivery(first,firstDiagnosis).status());
        assertEquals(1,delivery(second,secondDiagnosis).attemptCount());
        assertThrows(org.springframework.dao.DataAccessException.class,
                ()->jdbc.queryForObject("select count(*) from public.notification_destination_gate",Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings={"invalid","forbidden","archived","malformed","oversized"})
    void providerOutcomesRemainDistinctForPermanentAndUnconfirmedResponses(String response) throws Exception {
        var app=application(8863,true);
        var diagnosis=complete(app);
        MODE.set(response);
        worker.deliver(requireClaim(app));
        var result=delivery(app,diagnosis);
        String status=switch(response) {
            case "malformed","oversized" -> "DELIVERY_UNKNOWN";
            default -> "FAILED_TERMINAL";
        };
        assertEquals(status,result.status());
        assertEquals(1,RECEIVED.get());
        assertNull(result.acknowledgedAt());
        assertNull(queue.claim(app.org(),UUID.randomUUID()));
    }

    @Test
    void lostResponseRequiresExplicitConfirmationAndViewerCannotRetry() throws Exception {
        var app=application(8804,true);
        var diagnosis=complete(app);
        MODE.set("lost");
        worker.deliver(requireClaim(app));
        var result=delivery(app,diagnosis);
        assertEquals(1,RECEIVED.get());
        assertTrue(result.operatorReviewRequired());
        assertEquals("DELIVERY_UNKNOWN",result.status());
        assertNull(queue.claim(app.org(),UUID.randomUUID()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->access.retry(app.org(),app.actor,result.id(),false)).getStatusCode().value());
        organizations.setMember(app.org(),app.actor,8899,OrganizationRole.VIEWER);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.retry(app.org(),8899,result.id(),true)).getStatusCode().value());
        assertEquals("PENDING",access.retry(app.org(),app.actor,result.id(),true).status());
        MODE.set("ok");
        worker.deliver(requireClaim(app));
        assertEquals("DELIVERED",delivery(app,diagnosis).status());
        assertEquals(Long.toString(app.actor),access.attempts(app.org(),app.actor,result.id(),0).content().getFirst().requestedBy());
    }

    @Test
    void expiredPreDispatchLeaseIsRecoveredAndExpiredDispatchIsUncertain() throws Exception {
        var app=application(8805,true);
        var diagnosis=complete(app);
        var before=requireClaim(app);
        assertTrue(queue.heartbeat(before));
        expire(before);
        assertNull(queue.claim(app.org(),UUID.randomUUID()));
        assertNull(queue.begin(before));
        var after=requireClaim(app);
        assertEquals(before.id(),after.id());
        assertNotNull(queue.begin(after));
        expire(after);
        assertNull(queue.claim(app.org(),UUID.randomUUID()));
        assertFalse(queue.finish(after,new NotificationQueue.Result("DELIVERED",null,200,0)));
        assertEquals("DELIVERY_UNKNOWN",delivery(app,diagnosis).status());
        assertEquals(0,RECEIVED.get());
    }

    @Test
    void rotationBeforeDispatchCancelsWithoutSendingAndMissingDestinationSkips() throws Exception {
        var app=application(8806,true);
        var diagnosis=complete(app);
        var leased=requireClaim(app);
        applications.setWebhook(app.org(),app.actor,app.binding.applicationId(),webhook("replacement"));
        worker.deliver(leased);
        assertEquals("CANCELLED",delivery(app,diagnosis).status());
        assertEquals(0,RECEIVED.get());
        var off=application(8816,true);
        applications.setSlackPolicy(off.org(),off.actor,off.binding.applicationId(),false,1);
        assertEquals("SKIPPED",delivery(off,complete(off)).status());
        var missing=application(8826,false);
        assertEquals("DESTINATION_MISSING",delivery(missing,complete(missing)).failureCategory());
        var revoked=application(8836,true);
        var revokedDiagnosis=complete(revoked);
        var revokedClaim=requireClaim(revoked);
        applications.deactivate(revoked.org(),revoked.actor,revoked.binding.applicationId());
        worker.deliver(revokedClaim);
        assertEquals("CANCELLED",delivery(revoked,revokedDiagnosis).status());
        assertEquals(0,RECEIVED.get());
    }

    @Test
    void competingWorkersCreateOneAttemptAndNeverFollowRedirect() throws Exception {
        var app=application(8807,true);
        var diagnosis=complete(app);
        MODE.set("redirect");
        try (var threads=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=threads.submit(()->queue.claim(app.org(),UUID.randomUUID()));
            var b=threads.submit(()->queue.claim(app.org(),UUID.randomUUID()));
            var first=a.get(5,TimeUnit.SECONDS);
            var second=b.get(5,TimeUnit.SECONDS);
            assertTrue((first==null)!=(second==null));
            worker.deliver(first==null?second:first);
        }
        assertEquals(1,RECEIVED.get());
        var result=delivery(app,diagnosis);
        assertEquals("FAILED_TERMINAL",result.status());
        assertEquals("REDIRECT_BLOCKED",result.failureCategory());
        assertEquals(1,result.attemptCount());
    }

    @Test
    void multiApplicationIncidentUsesOwnerDestinationAndOmitsSharedDiagnosisText() throws Exception {
        var owner=application(8808,true);
        var peer=addApplication(owner.org(),owner.actor,"related-app",true);
        String trace="00-a28d54f575dc40b886a7592a8fc0a76a-1da757821fb81a35-01";
        submit(owner,trace);
        submit(peer,trace);
        var claim=diagnoses.claim(owner.org(),UUID.randomUUID());
        assertNotNull(claim);
        assertTrue(diagnoses.succeed(claim,MODEL));
        worker.deliver(requireClaim(owner));
        assertEquals(1,RECEIVED.get());
        assertTrue(PAYLOAD.get().contains("payment-service"));
        assertFalse(PAYLOAD.get().contains("related-app"));
        assertFalse(PAYLOAD.get().contains("Fixture cause"));
        assertTrue(PAYLOAD.get().contains("spans applications"));
        assertEquals(1,count(owner.org()));
    }

    private record App(long actor,Binding binding) {
        UUID org() { return binding.organizationId(); }
    }

    private App application(long actor,boolean configured) {
        UUID organization=organizations.create(actor,"Notification fixture","s08-"+UUID.randomUUID()).id();
        return addApplication(organization,actor,"payment-service",configured);
    }

    private App addApplication(UUID organization,long actor,String name,boolean configured) {
        long app=Long.parseLong(applications.create(organization,actor,"fixture@example.invalid",name,PYTHON,
                Environment.DEVELOPMENT,null).id());
        if(configured) applications.setWebhook(organization,actor,app,webhook("fixture-" + app));
        return new App(actor,new Binding(organization,app,name,Environment.DEVELOPMENT));
    }

    private static String webhook(String suffix) {
        return "https://hooks.slack.com/services/FIXTURE/NOT_REAL/" + suffix;
    }

    private void submit(App app,String trace) throws Exception {
        var body=json.createObjectNode().put("applicationName",app.binding.applicationName()).put("environment","DEVELOPMENT");
        var event=body.putArray("events").addObject().put("eventId",UUID.randomUUID().toString())
                .put("timestamp",Instant.now().toString()).put("level","ERROR").put("source","PYTHON")
                .put("message","Synthetic payment database connection failure");
        if(trace!=null) event.put("traceparent",trace);
        ingestion.submit(json.writeValueAsBytes(body),app.binding);
    }

    private DiagnosisQueue.Claim diagnose(App app,String trace) throws Exception {
        submit(app,trace);
        var claim=diagnoses.claim(app.org(),UUID.randomUUID());
        assertNotNull(claim);
        return claim;
    }

    private DiagnosisQueue.Claim complete(App app) throws Exception {
        var claim=diagnose(app,null);
        assertTrue(diagnoses.succeed(claim,MODEL));
        return claim;
    }

    private NotificationApi.Delivery delivery(App app,DiagnosisQueue.Claim claim) {
        return access.incident(app.org(),app.actor,claim.incident(),0).content().getFirst();
    }

    private NotificationQueue.Claim requireClaim(App app) {
        var claim=queue.claim(app.org(),UUID.randomUUID());
        assertNotNull(claim);
        return claim;
    }

    private void expire(NotificationQueue.Claim claim) {
        transactions.executeWithoutResult(tx -> {
            scope.enter(claim.organization());
            assertEquals(1,jdbc.update("update public.notification_outbox set lease_until=clock_timestamp() where id=?",claim.id()));
        });
    }

    private int count(UUID organization) {
        return transactions.execute(tx -> {
            scope.enter(organization);
            return jdbc.queryForObject("select count(*) from public.notification_outbox",Integer.class);
        });
    }
}
