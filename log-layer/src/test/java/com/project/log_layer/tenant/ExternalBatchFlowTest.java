package com.project.log_layer.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.security.SignedIdentity;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "tenant.limits.ingest-per-minute=12")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE03_FIXTURE", matches = "true")
class ExternalBatchFlowTest {
    private static final String PATH = "/api/v1/logs/batch";
    private static final MlPredictionResponse NORMAL =
            new MlPredictionResponse(1, "Normal", false, 0.03, "2.0.0-candidate");

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired TenantSqlScope scope;
    @Autowired ObjectMapper json;
    @Value("${JWT_SECRET}") String signingSecret;
    @MockitoBean MlInferenceClient inference;
    @MockitoBean LlmInferenceClient diagnosis;

    @BeforeEach
    void stubInference() {
        when(inference.predict(any())).thenReturn(NORMAL);
    }

    @Test
    void atomicRetriesConflictAndApplicationScope() throws Exception {
        Owner a = owner(103L);
        Owner b = owner(104L);
        ObjectNode first = event("Normal payment accepted");
        ObjectNode second = event("Normal payment accepted");
        String batch = body(first, second);
        var original = parse(send(a, batch).andExpect(status().isOk())).path("events");
        assertEquals("ACCEPTED", original.get(0).path("status").asText());
        assertNotEquals(original.get(0).path("logId"), original.get(1).path("logId"));
        var retry = parse(send(a, batch).andExpect(status().isOk())).path("events").get(0);
        assertEquals("DUPLICATE", retry.path("status").asText());
        assertEquals(original.get(0).path("logId"), retry.path("logId"));
        var other = parse(send(b, body(first)).andExpect(status().isOk())).path("events").get(0);
        assertEquals("ACCEPTED", other.path("status").asText());
        assertNotEquals(original.get(0).path("logId"), other.path("logId"));

        ObjectNode unsaved = event("Candidate for rollback");
        send(a, body(unsaved, first.deepCopy().put("message", "Changed"))).andExpect(status().isConflict());
        send(a, body(unsaved, event(" "))).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from public.logs where external_event_id=?",
                Integer.class, UUID.fromString(unsaved.path("eventId").asText())));
        verify(inference, times(3)).predict(any());

        long idA = original.get(0).path("logId").asLong();
        long idB = other.path("logId").asLong();
        http.perform(get("/api/v1/logs/" + idB).header("Authorization", a.token())
                        .header("X-DevInsight-Organization", a.org())).andExpect(status().isNotFound());
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL ROLE devinsight_log_tenant");
            assertEquals(0, jdbc.queryForObject("select count(*) from public.logs", Integer.class));
            jdbc.queryForObject("select set_config('devinsight.organization_id', ?, true)", String.class, a.org());
            assertEquals(1, jdbc.queryForObject("select count(*) from public.logs where id=?", Integer.class, idA));
            assertEquals(0, jdbc.queryForObject("select count(*) from public.logs where id=?", Integer.class, idB));
        });
    }

    @Test
    void validatesBeforeChargingWeightedLimitsAndRejectsRevokedKeys() throws Exception {
        Owner a = owner(105L);
        String content = body(event("Normal payment accepted"));
        http.perform(post(PATH).contentType("application/json").content(content)
                .header("X-User-Id", "105").header("X-DevInsight-Organization", a.org())).andExpect(status().isUnauthorized());
        http.perform(post(PATH).contentType("application/json").content(content)
                .header("Authorization", a.token())).andExpect(status().isUnauthorized());
        http.perform(post(PATH).contentType("application/json").content(content)
                .header("X-API-Key", a.key()).header("Content-Encoding", "gzip")).andExpect(status().isUnsupportedMediaType());
        http.perform(post(PATH).contentType("text/plain").content(content)
                .header("X-API-Key", a.key())).andExpect(status().isUnsupportedMediaType());
        send(a, content.replace("fixture-payments", "different")).andExpect(status().isForbidden());
        send(a, content.replace("\"events\":", "\"organizationId\":\"" + a.org() + "\",\"events\":"))
                .andExpect(status().isBadRequest());
        send(a, body(event("x".repeat(66000)))).andExpect(status().isPayloadTooLarge());
        send(a, body(event("λ".repeat(8300)))).andExpect(status().isPayloadTooLarge());
        send(a, body(event("bad date").put("timestamp", "2026-11-21T02:15:33"))).andExpect(status().isBadRequest());
        ObjectNode metadata = event("metadata");
        metadata.putObject("metadata").put("apiKey", "synthetic-value");
        send(a, body(metadata)).andExpect(status().isBadRequest());

        ObjectNode[] large = new ObjectNode[10];
        for (int i = 0; i < large.length; i++) large[i] = event("payment " + i);
        send(a, body(large)).andExpect(status().isOk());
        send(a, body(event("A"), event("B"), event("C"))).andExpect(status().isTooManyRequests());
        http.perform(delete(a.credentials() + "/" + a.credentialId()).header("Authorization", a.token()))
                .andExpect(status().isNoContent());
        send(a, content).andExpect(status().isUnauthorized());
        verify(inference, times(10)).predict(any());
    }

    @Test
    void concurrentDuplicateOnlyScoresAndPersistsOneDiagnosisJob() throws Exception {
        Owner a = owner(106L);
        String batch = body(event("Database connection timed out during checkout").put("level", "ERROR"));
        var scoring = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(inference.predict(any())).thenAnswer(invocation -> {
            scoring.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
            return NORMAL;
        });
        long logId;
        try (var callers = Executors.newSingleThreadExecutor()) {
            var first = callers.submit(() -> parse(send(a, batch).andExpect(status().isOk())));
            try {
                assertTrue(scoring.await(5, TimeUnit.SECONDS));
                send(a, batch).andExpect(status().isServiceUnavailable());
            } finally {
                release.countDown();
            }
            var original = first.get(5, TimeUnit.SECONDS).path("events").get(0);
            var retry = parse(send(a, batch).andExpect(status().isOk())).path("events").get(0);
            assertEquals("DUPLICATE", retry.path("status").asText());
            assertEquals(original.path("logId"), retry.path("logId"));
            logId = original.path("logId").asLong();
        }
        verify(inference, times(1)).predict(any());
        verifyNoInteractions(diagnosis);
        transaction.executeWithoutResult(status -> {
            scope.enter(UUID.fromString(a.org()));
            assertEquals(1, jdbc.queryForObject("select count(*) from public.diagnosis_jobs where log_id=?",
                    Integer.class, logId));
            assertEquals("PENDING", jdbc.queryForObject("select status from public.diagnosis_jobs where log_id=?",
                    String.class, logId));
        });
    }

    private ResultActions send(Owner owner, String body) throws Exception {
        return http.perform(post(PATH).header("X-API-Key", owner.key()).contentType("application/json").content(body));
    }

    private String body(ObjectNode... events) {
        var collection = json.createArrayNode();
        for (var event : events) collection.add(event);
        var envelope = json.createObjectNode().put("applicationName", "fixture-payments").put("environment", "DEVELOPMENT");
        envelope.set("events", collection);
        return envelope.toString();
    }

    private ObjectNode event(String message) {
        return json.createObjectNode().put("eventId", UUID.randomUUID().toString())
                .put("timestamp", "2026-11-21T02:15:33+05:30").put("level", "INFO").put("source", "PYTHON")
                .put("message", message);
    }

    private Owner owner(long user) throws Exception {
        var key = SignedIdentity.signingKey(signingSecret);
        String token = "Bearer " + Jwts.builder().subject("fixture" + user + "@example.test")
                .claim("user_id", user).claim("role", "DEVELOPER").expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(key, SignedIdentity.signingAlgorithm(key)).compact();
        String slug = "fixture-" + UUID.randomUUID();
        var org = parse(http.perform(post("/api/v1/organizations").header("Authorization", token)
                        .contentType("application/json").content("{\"name\":\"Fixture\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())).path("id").asText();
        var apps = "/api/v1/organizations/" + org + "/applications";
        var app = parse(http.perform(post(apps).header("Authorization", token).contentType("application/json")
                        .content("{\"applicationName\":\"fixture-payments\",\"techStack\":\"PYTHON\",\"environment\":\"DEVELOPMENT\"}"))
                .andExpect(status().isCreated())).path("id").asText();
        var credentials = apps + "/" + app + "/credentials";
        var credential = parse(http.perform(post(credentials).header("Authorization", token).contentType("application/json")
                .content("{\"name\":\"Disposable test\"}")).andExpect(status().isCreated()));
        return new Owner(token, org, credentials, credential.path("id").asText(), credential.path("key").asText());
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private record Owner(String token, String org, String credentials, String credentialId, String key) {}
}
