package com.project.log_layer.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.security.SignedIdentity;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "tenant.limits.ingest-per-minute=3")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE02_FIXTURE", matches = "true")
class TenantFlowTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    @Value("${JWT_SECRET}") String signingSecret;
    @Value("${internal.api-key}") String internalKey;
    @MockitoBean MlInferenceClient inference;
    @MockitoBean UserDirectory directory;

    @Test
    void tenantOwnershipCredentialLifecycleAndRls() throws Exception {
        when(inference.predict(any())).thenReturn(new MlPredictionResponse(1, "Normal", false, 0.03, "2.0.0-candidate"));
        String a = token(101L);
        String b = token(102L);
        String orgA = createOrganization("alpha-" + UUID.randomUUID(), a);
        String orgB = createOrganization("beta-" + UUID.randomUUID(), b);
        String pathA = "/api/v1/organizations/" + orgA;
        String pathB = "/api/v1/organizations/" + orgB;

        http.perform(get(pathB).header("Authorization", a)).andExpect(status().isNotFound());
        http.perform(get(pathA).header("X-User-Id", "101")).andExpect(status().isUnauthorized());
        http.perform(get(pathA).header("Authorization", token(null))).andExpect(status().isUnauthorized());
        http.perform(delete(pathA + "/members/101").header("Authorization", a)).andExpect(status().isConflict());
        http.perform(put(pathB + "/members/101").header("Authorization", a).contentType("application/json")
                .content("{\"role\":\"OWNER\"}")).andExpect(status().isNotFound());

        String appA = createApplication(pathA, a);
        String appB = createApplication(pathB, b);
        createAppRequest(pathA, a).andExpect(status().isConflict());
        http.perform(get(pathB + "/applications/" + appB).header("Authorization", a))
                .andExpect(status().isNotFound());
        http.perform(get("/api/v1/integrations").header("Authorization", a)).andExpect(status().isGone());

        String credentialsA = pathA + "/applications/" + appA + "/credentials";
        String credentialsB = pathB + "/applications/" + appB + "/credentials";
        JsonNode issuedA = key(credentialsA, a);
        JsonNode issuedB = key(credentialsB, b);
        String keyA = issuedA.path("key").asText();
        String keyB = issuedB.path("key").asText();
        assertNotEquals(keyA, keyB);
        assertEquals(32, jdbc.queryForObject("select octet_length(key_hash) from public.application_credentials where id=?",
                Integer.class, UUID.fromString(issuedA.path("id").asText())));
        http.perform(get(credentialsA).header("Authorization", a))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist());
        http.perform(delete(credentialsB + "/" + issuedB.path("id").asText()).header("Authorization", a))
                .andExpect(status().isNotFound());

        http.perform(post(credentialsA).header("Authorization", a).contentType("application/json")
                        .content("{\"name\":\"Expired at issue\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        JsonNode shortLived = payload(http.perform(post(credentialsA).header("Authorization", a)
                        .contentType("application/json").content(json.createObjectNode()
                                .put("name", "Short-lived fixture")
                                .put("expiresAt", Instant.now().plusSeconds(3600).toString()).toString()))
                .andExpect(status().isCreated()));
        http.perform(get("/api/v1/integrations/verify-key").header("X-Internal-Api-Key", internalKey)
                        .header("X-API-Key", shortLived.path("key").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        jdbc.update("update public.application_credentials set expires_at=now()-interval '1 second' where id=?",
                UUID.fromString(shortLived.path("id").asText()));
        ingest(shortLived.path("key").asText(), appA).andExpect(status().isUnauthorized());

        ingest(keyA, appB).andExpect(status().isForbidden());
        long logA = payload(ingest(keyA, appA).andExpect(status().isCreated())).path("id").asLong();
        long logB = payload(ingest(keyB, appB).andExpect(status().isCreated())).path("id").asLong();
        http.perform(get("/api/v1/logs/" + logA).header("Authorization", a).header("X-DevInsight-Organization", orgA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelVersion").value("2.0.0-candidate"));
        http.perform(get("/api/v1/logs/" + logB).header("Authorization", a).header("X-DevInsight-Organization", orgA))
                .andExpect(status().isNotFound());

        long start = System.nanoTime();
        http.perform(delete(credentialsA + "/" + issuedA.path("id").asText()).header("Authorization", a))
                .andExpect(status().isNoContent());
        ingest(keyA, appA).andExpect(status().isUnauthorized());
        System.out.println("Committed revocation and next local request completed in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
        http.perform(get("/api/v1/logs/" + logB).header("X-API-Key", keyB)).andExpect(status().isUnauthorized());

        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL ROLE devinsight_log_tenant");
            assertEquals(0, jdbc.queryForObject("select count(*) from public.logs", Integer.class));
            jdbc.queryForObject("select set_config('devinsight.organization_id', ?, true)", String.class, orgA);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.logs where id=?", Integer.class, logA));
            assertEquals(0, jdbc.queryForObject("select count(*) from public.logs where id=?", Integer.class, logB));
        });
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL ROLE devinsight_log_tenant");
            assertEquals(0, jdbc.queryForObject("select count(*) from public.logs", Integer.class));
        });
        http.perform(post("/api/v1/logs").header("X-API-Key", keyB).contentType("application/json")
                .content("x".repeat(66000))).andExpect(status().isPayloadTooLarge());
        boolean throttled = false;
        for (int i = 0; i < 5; i++) {
            int code = ingest(keyB, appA).andReturn().getResponse().getStatus();
            if (code == 429) {
                throttled = true;
                break;
            }
            assertEquals(403, code);
        }
        assertTrue(throttled, "Authenticated ingestion attempts have a per-application cap");

        assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL ROLE devinsight_log_tenant");
            jdbc.queryForObject("select set_config('devinsight.organization_id', ?, true)", String.class, orgA);
            jdbc.update("update public.logs set organization_id=?, application_id=null where id=?",
                    UUID.fromString(orgB), logA);
        }));
    }

    private String token(Long userId) {
        var key = SignedIdentity.signingKey(signingSecret);
        var builder = Jwts.builder().subject("actor-" + userId + "@example.test").claim("role", "DEVELOPER")
                .expiration(Date.from(Instant.now().plusSeconds(300)));
        if (userId != null) builder.claim("user_id", userId);
        return "Bearer " + builder.signWith(key, SignedIdentity.signingAlgorithm(key)).compact();
    }

    private String createOrganization(String slug, String token) throws Exception {
        return payload(http.perform(post("/api/v1/organizations").header("Authorization", token)
                        .contentType("application/json").content("{\"name\":\"Tenant\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())).path("id").asText();
    }

    private ResultActions createAppRequest(String orgPath, String token) throws Exception {
        return http.perform(post(orgPath + "/applications").header("Authorization", token).contentType("application/json")
                .content("{\"applicationName\":\"payment-service\",\"techStack\":\"SPRING_BOOT\",\"environment\":\"DEVELOPMENT\"}"));
    }

    private String createApplication(String orgPath, String token) throws Exception {
        return payload(createAppRequest(orgPath, token).andExpect(status().isCreated())).path("id").asText();
    }

    private JsonNode key(String url, String token) throws Exception {
        return payload(http.perform(post(url).header("Authorization", token).contentType("application/json")
                .content("{\"name\":\"Fixture\"}")).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store")));
    }

    private ResultActions ingest(String rawKey, String app) throws Exception {
        return http.perform(post("/api/v1/logs").header("X-API-Key", rawKey).contentType("application/json")
                .content("""
                        {"rawLog":"2026-11-20 12:00:00 INFO payment-service - test payment approved",
                         "source":"SPRING_BOOT","environment":"DEVELOPMENT","applicationName":"payment-service",
                         "applicationId":%s}
                        """.formatted(app)));
    }

    private JsonNode payload(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray());
    }
}
