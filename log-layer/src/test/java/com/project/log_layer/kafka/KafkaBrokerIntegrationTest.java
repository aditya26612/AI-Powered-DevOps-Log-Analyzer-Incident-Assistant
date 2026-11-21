package com.project.log_layer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.service.ExternalBatchIngestion;
import com.project.log_layer.tenant.ApplicationCredentials;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.tenant.TenantApplications;
import com.project.log_layer.tenant.TenantSqlScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.*;
import org.apache.kafka.common.resource.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55438/log_layer_db",
    "spring.datasource.username=devinsight_log_app",
    "spring.datasource.password=${DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD}",
    "spring.datasource.hikari.connection-timeout=2000",
    "spring.datasource.hikari.validation-timeout=1000",
    "spring.jpa.hibernate.ddl-auto=validate",
    "devinsight.kafka.enabled=true",
    "devinsight.kafka.consumer-enabled=false",
    "devinsight.kafka.bootstrap=127.0.0.1:19094",
    "devinsight.kafka.producer-username=devinsight-writer",
    "devinsight.kafka.producer-password=${DEVINSIGHT_STAGE04_WRITER}",
    "devinsight.kafka.consumer-username=devinsight-worker",
    "devinsight.kafka.consumer-password=${DEVINSIGHT_STAGE04_WORKER}"
})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "DEVINSIGHT_STAGE04_FIXTURE", matches = "true")
class KafkaBrokerIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired KafkaMode mode;
    @Autowired KafkaLagMonitor monitor;
    @Autowired KafkaRecordProcessor processor;
    @Autowired OrganizationService organizations;
    @Autowired TenantApplications applications;
    @Autowired ApplicationCredentials credentials;
    @Autowired TenantSqlScope scope;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MlInferenceClient ml;

    @BeforeEach
    void setup() {
        when(ml.predict(any())).thenReturn(new MlPredictionResponse(1, "Normal", false, 0.04, "2.0.0-candidate"));
        await().atMost(Duration.ofSeconds(20)).until(monitor::healthy);
    }

    @Test
    void brokerAckIsIndependentOfCommitAndRestartReplaysCommittedIdentityOnce() throws Exception {
        Owner owner = owner(9101);
        UUID id = UUID.randomUUID();
        String body = payload(owner, id, "ERROR");
        var response = http.perform(post("/api/v2/logs/batch").header("X-API-Key", owner.key())
                .contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.acknowledgement").value("BROKER_ACCEPTED"))
                .andExpect(jsonPath("$.events[0].eventId").value(id.toString()))
                .andExpect(jsonPath("$.events[0].logId").doesNotExist()).andReturn();
        UUID receiptId = UUID.fromString(json.readTree(response.getResponse().getContentAsByteArray()).path("receiptId").asText());
        assertEquals(0, rows(owner, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from public.logs", Integer.class));
        try (var admin = admin(); var crashed = consumer()) {
            var pending = find(crashed, receiptId);
            var partition = new TopicPartition(pending.topic(), pending.partition());
            assertTrue(committed(admin, partition) <= pending.offset());
            var stored = processor.process(pending).events().getFirst();
            assertEquals(ExternalBatchIngestion.Status.ACCEPTED, stored.status());
            assertEquals(1, rows(owner, id));
            UUID incident = singleIncident(owner, id);
            // Closing this consumer before the commit call forces the real group to replay.
            crashed.close(Duration.ofSeconds(1));
            assertTrue(committed(admin, partition) <= pending.offset());
            var meters = new SimpleMeterRegistry();
            var restarted = new KafkaWorker(mode, processor, meters);
            restarted.start();
            try {
                await().atMost(Duration.ofSeconds(30)).until(() -> committed(admin, partition) > pending.offset());
                assertTrue(meters.counter("devinsight.kafka.consumed.events", "result", "duplicate").count() >= 1);
                assertEquals(1, rows(owner, id));
                assertEquals(incident, singleIncident(owner, id));
                verify(ml, times(1)).predict(any());
            } finally {
                restarted.stop();
                restarted.closePublisher();
            }
        }
    }

    @Test
    void poisonWaitsForConfirmedDlqAndGroupRebalanceProcessesEntriesInOrder() throws Exception {
        Owner a = owner(9102);
        Owner b = owner(9103);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var meters = new SimpleMeterRegistry();
        try (var admin = admin(); var writer = producer(false)) {
            AclBinding rule = dlqRule();
            admin.deleteAcls(List.of(rule.toFilter())).all().get(5, TimeUnit.SECONDS);
            long initialDeadLetters = end(admin, new TopicPartition(mode.getDeadLetterTopic(), 0));
            RecordMetadata invalid = writer.send(new ProducerRecord<>(mode.getRawTopic(), a.partitionKey(),
                    "not-json".getBytes(StandardCharsets.UTF_8))).get(8, TimeUnit.SECONDS);
            var partition = new TopicPartition(invalid.topic(), invalid.partition());
            send(a, first);
            send(a, second);
            send(b, first);
            var worker = new KafkaWorker(mode, processor, meters);
            var peer = new KafkaWorker(mode, processor, meters);
            worker.start();
            try {
                await().atMost(Duration.ofSeconds(20)).until(() -> meters.counter("devinsight.kafka.worker.operations",
                        "result", "dlq_unconfirmed").count() > 0);
                assertTrue(committed(admin, partition) <= invalid.offset());
                assertEquals(0, rows(a, first));
                assertEquals(initialDeadLetters, end(admin, new TopicPartition(mode.getDeadLetterTopic(), 0)));
                admin.createAcls(List.of(rule)).all().get(5, TimeUnit.SECONDS);
                peer.start();
                await().atMost(Duration.ofSeconds(35)).until(() ->
                        rows(a, first) == 1 && rows(a, second) == 1 && rows(b, first) == 1
                        && committed(admin, partition) > invalid.offset());
                assertEquals(initialDeadLetters + 1, end(admin, new TopicPartition(mode.getDeadLetterTopic(), 0)));
                assertTrue(logId(a, first) < logId(a, second));
                assertNotEquals(logId(a, first), logId(b, first));
                await().atMost(Duration.ofSeconds(20)).until(() -> meters.counter("devinsight.kafka.worker.operations",
                        "result", "assigned").count() >= 2);
                worker.stop();
                UUID next = UUID.randomUUID();
                send(a, next);
                await().atMost(Duration.ofSeconds(30)).until(() -> rows(a, next) == 1);
            } finally {
                admin.createAcls(List.of(rule)).all().get(5, TimeUnit.SECONDS);
                peer.stop();
                peer.closePublisher();
                worker.stop();
                worker.closePublisher();
            }
        }
    }

    @Test
    void appAuthenticationAndKafkaRoleRestrictionsRemainIndependent() throws Exception {
        Owner owner = owner(9104);
        String valid = payload(owner, UUID.randomUUID(), "INFO");
        http.perform(post("/api/v2/logs/batch").contentType("application/json").content(valid)
                .header("X-Organization-Id", owner.org().toString())).andExpect(status().isUnauthorized());
        http.perform(post("/api/v2/logs/batch").contentType("application/json").content(valid.replace("payment-service", "other"))
                .header("X-API-Key", owner.key())).andExpect(status().isForbidden());
        http.perform(post("/api/v2/logs/batch").contentType("application/json").content(valid.replace("DEVELOPMENT", "PRODUCTION"))
                .header("X-API-Key", owner.key())).andExpect(status().isForbidden());
        http.perform(post("/api/v2/logs/batch").contentType("application/json")
                .content(valid.replace("\"events\":", "\"organizationId\":\""+UUID.randomUUID()+"\",\"events\":"))
                .header("X-API-Key", owner.key())).andExpect(status().isBadRequest());
        credentials.revoke(owner.org(), owner.app(), owner.credential());
        http.perform(post("/api/v2/logs/batch").contentType("application/json").content(valid)
                .header("X-API-Key", owner.key())).andExpect(status().isUnauthorized());
        var expiring = credentials.create(owner.org(), owner.app(), "Expiring fixture", OffsetDateTime.now().plusSeconds(60));
        jdbc.update("update public.application_credentials set expires_at=now()-interval '1 second' where id=?", expiring.id());
        http.perform(post("/api/v2/logs/batch").contentType("application/json").content(valid)
                .header("X-API-Key", expiring.key())).andExpect(status().isUnauthorized());

        byte[] synthetic = "{}".getBytes(StandardCharsets.UTF_8);
        try (var writer = producer(false); var worker = producer(true)) {
            var a = assertThrows(ExecutionException.class, () -> writer.send(new ProducerRecord<>(mode.getDeadLetterTopic(),
                    owner.partitionKey(), synthetic)).get(8, TimeUnit.SECONDS));
            assertInstanceOf(org.apache.kafka.common.errors.TopicAuthorizationException.class, a.getCause());
            var b = assertThrows(ExecutionException.class, () -> worker.send(new ProducerRecord<>(mode.getRawTopic(),
                    owner.partitionKey(), synthetic)).get(8, TimeUnit.SECONDS));
            assertInstanceOf(org.apache.kafka.common.errors.TopicAuthorizationException.class, b.getCause());
        }
        var writerConsumer = new HashMap<>(KafkaWorker.consumerProperties(mode, "unauthorized-reader"));
        writerConsumer.putAll(mode.connection(false));
        try (var restricted = new KafkaConsumer<>(writerConsumer, new StringDeserializer(), new ByteArrayDeserializer())) {
            var tp = new TopicPartition(mode.getRawTopic(), 0);
            restricted.assign(List.of(tp));
            restricted.seek(tp, 0);
            assertThrows(org.apache.kafka.common.errors.TopicAuthorizationException.class, () -> {
                Instant until = Instant.now().plusSeconds(8);
                while (Instant.now().isBefore(until)) restricted.poll(Duration.ofMillis(500));
            });
        }
        try (var unauthenticated = Admin.create(Map.of("bootstrap.servers", "127.0.0.1:19094",
                "request.timeout.ms", 1000, "default.api.timeout.ms", 1500))) {
            assertThrows(ExecutionException.class,
                    () -> unauthenticated.describeCluster().nodes().get(4, TimeUnit.SECONDS));
        }
    }

    @Test
    void databaseOutageKeepsOffsetAndRecoveryPersistsTheEvent() throws Exception {
        Owner a = owner(9105);
        UUID id = UUID.randomUUID();
        UUID receipt = send(a, id);
        var pgdata = Path.of(System.getenv("DEVINSIGHT_STAGE04_PGDATA")).toAbsolutePath().normalize();
        try (var connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55438/log_layer_db",
                "postgres", System.getenv("DB_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("select current_setting('data_directory')")) {
            assertTrue(result.next());
            assertEquals(pgdata, Path.of(result.getString(1)).toAbsolutePath().normalize());
        }
        assertEquals(55438, jdbc.queryForObject("select inet_server_port()", Integer.class));
        assertEquals("pgdata-verified", pgdata.getFileName().toString());
        var meters = new SimpleMeterRegistry();
        try (var admin = admin(); var probe = consumer()) {
            var pending = find(probe, receipt);
            var tp = new TopicPartition(pending.topic(), pending.partition());
            var worker = new KafkaWorker(mode, processor, meters);
            try {
                pg(pgdata, false);
                try {
                    worker.start();
                    // A forcibly stopped fixture consumer can retain its group membership during rebalancing.
                    await().atMost(Duration.ofSeconds(55)).until(() -> meters.counter("devinsight.kafka.worker.operations",
                            "result", "processing_retry").count() > 0);
                    assertTrue(committed(admin, tp) <= pending.offset());
                } finally {
                    pg(pgdata, true);
                }
                await().atMost(Duration.ofSeconds(30)).until(() -> rows(a, id) == 1 && committed(admin,tp) > pending.offset());
            } finally {
                worker.stop();
                worker.closePublisher();
            }
        }
    }

    @Test
    void operatorCanReplayOneValidatedDeadLetterAfterItsAssociationIsRestored() throws Exception {
        UUID org = organizations.create(9106, "Replay fixture", "replay-" + UUID.randomUUID()).id();
        Long app = jdbc.queryForObject("select nextval('public.application_integrations_id_seq')", Long.class);
        UUID id = UUID.randomUUID();
        var owner = new Owner(org, app, null, null);
        var binding = new ApplicationCredentials.Binding(org, app, "payment-service", Environment.DEVELOPMENT);
        var events = List.of(new com.project.log_layer.dto.request.ingest.ExternalLogEvent(id, Instant.now().toString(),
                com.project.log_layer.enums.LogLevel.INFO, com.project.log_layer.enums.LogSource.PYTHON,
                "Controlled replay fixture", null, null, null, null, null, Map.of()));
        var envelope = BrokerLogEnvelope.create(binding, events);
        var dlq = new TopicPartition(mode.getDeadLetterTopic(), 0);
        var meters = new SimpleMeterRegistry();
        try (var admin = admin(); var writer = producer(false)) {
            long offset = end(admin, dlq);
            writer.send(new ProducerRecord<>(mode.getRawTopic(), owner.partitionKey(),
                    BrokerLogEnvelope.JSON.writeValueAsBytes(envelope))).get(8, TimeUnit.SECONDS);
            var runner = new KafkaWorker(mode, processor, meters);
            runner.start();
            try {
                await().atMost(Duration.ofSeconds(20)).until(() -> end(admin, dlq) == offset + 1);
                assertEquals(0, rows(owner, id));
                assertEquals(1, replay(offset, UUID.randomUUID(), app, false));
                assertEquals(0, replay(offset, org, app, false));
                jdbc.update("""
                        insert into public.application_integrations (id,organization_id,application_name,
                            environment,tech_stack,user_email,alert_on_anomaly,alert_on_error,created_at,updated_at)
                        values (?,?,'payment-service','DEVELOPMENT','PYTHON','fixture@example.invalid',false,false,now(),now())
                        """, app, org);
                assertEquals(0, replay(offset, org, app, true));
                await().atMost(Duration.ofSeconds(20)).until(() -> rows(owner, id) == 1);
                long stored = logId(owner, id);
                assertEquals(0, replay(offset, org, app, true));
                await().atMost(Duration.ofSeconds(20)).until(() -> meters.counter("devinsight.kafka.consumed.events",
                        "result", "duplicate").count() > 0);
                assertEquals(stored, logId(owner, id));
                verify(ml, times(1)).predict(any());
            } finally {
                runner.stop();
                runner.closePublisher();
            }
        }
    }

    private static int replay(long offset, UUID org, long app, boolean execute) throws Exception {
        Path script = Path.of("..", "scripts", "replay-kafka-local.ps1").toAbsolutePath().normalize();
        var command = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-File", script.toString(), "-Fixture",
                "-Offset", Long.toString(offset), "-OrganizationId", org.toString(), "-ApplicationId", Long.toString(app)));
        if (execute) command.add("-Replay");
        Path diagnostics = Path.of(System.getenv("DEVINSIGHT_STAGE04_PGDATA")).resolveSibling("replay-" + org + ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(diagnostics.toFile());
        // Windows PowerShell resolves its own modules instead of inheriting PowerShell 7's module path.
        builder.environment().remove("PSModulePath");
        var action = builder.start();
        assertTrue(action.waitFor(25, TimeUnit.SECONDS));
        return action.exitValue();
    }

    private static void pg(Path data, boolean start) throws Exception {
        var command = new ArrayList<>(List.of("C:/Program Files/PostgreSQL/16/bin/pg_ctl.exe", "-D", data.toString(),
                "-w", "-t", "20"));
        command.addAll(start ? List.of("-l", data.resolveSibling("stage04-pg-outage.log").toString(),
                "-o", "-h 127.0.0.1 -p 55438", "start") : List.of("-m", "fast", "stop"));
        var action = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(action.waitFor(25, TimeUnit.SECONDS));
        assertEquals(0, action.exitValue());
    }

    private Owner owner(long id) {
        UUID org = organizations.create(id, "Kafka fixture", "kafka-" + UUID.randomUUID()).id();
        long app = Long.parseLong(applications.create(org, id, "kafka-fixture@example.invalid", "payment-service",
                TechStack.PYTHON, Environment.DEVELOPMENT, null).id());
        var issued = credentials.create(org, app, "Kafka fixture key", null);
        return new Owner(org, app, issued.id(), issued.key());
    }

    private String payload(Owner owner, UUID id, String level) {
        var batch = json.createObjectNode().put("applicationName", "payment-service").put("environment", "DEVELOPMENT");
        batch.putArray("events").addObject().put("eventId", id.toString()).put("timestamp", Instant.now().toString())
                .put("source", "PYTHON").put("level", level).put("message", "Controlled Kafka fixture");
        return batch.toString();
    }

    private UUID send(Owner owner, UUID id) throws Exception {
        var response = http.perform(post("/api/v2/logs/batch").header("X-API-Key", owner.key())
                .contentType("application/json").content(payload(owner, id, "INFO")))
                .andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(json.readTree(response.getResponse().getContentAsByteArray()).path("receiptId").asText());
    }

    private int rows(Owner owner, UUID id) {
        return transaction.execute(status -> {
            scope.enter(owner.org());
            return jdbc.queryForObject("select count(*) from public.logs where application_id=? and external_event_id=?",
                    Integer.class, owner.app(), id);
        });
    }

    private UUID singleIncident(Owner owner, UUID event) {
        return transaction.execute(status -> {
            scope.enter(owner.org());
            return jdbc.queryForObject("""
                    select i.id from public.incidents i
                    join public.incident_memberships m on m.organization_id=i.organization_id and m.incident_id=i.id
                    join public.logs l on l.organization_id=m.organization_id and l.id=m.log_id
                    where i.organization_id=? and l.external_event_id=? and i.event_count=1
                      and i.diagnosis_log_id=l.id and exists (select 1 from public.diagnosis_jobs j
                          where j.organization_id=i.organization_id and j.incident_id=i.id and j.log_id=l.id)
                    """, UUID.class, owner.org(), event);
        });
    }

    private long logId(Owner owner, UUID id) {
        return transaction.execute(status -> {
            scope.enter(owner.org());
            return jdbc.queryForObject("select id from public.logs where application_id=? and external_event_id=?",
                    Long.class, owner.app(), id);
        });
    }

    private KafkaConsumer<String, byte[]> consumer() {
        return new KafkaConsumer<>(KafkaWorker.consumerProperties(mode, "stage04-crash-probe"),
                new StringDeserializer(), new ByteArrayDeserializer());
    }

    private KafkaProducer<String, byte[]> producer(boolean worker) {
        var properties = new HashMap<>(mode.connection(worker));
        properties.put("max.block.ms", 3500);
        properties.put("request.timeout.ms", 1500);
        properties.put("delivery.timeout.ms", 4000);
        return new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer());
    }

    private ConsumerRecord<String, byte[]> find(KafkaConsumer<String, byte[]> reader, UUID receipt) {
        var partitions = java.util.stream.IntStream.range(0, 3)
                .mapToObj(p -> new TopicPartition(mode.getRawTopic(), p)).toList();
        reader.assign(partitions);
        reader.seekToBeginning(partitions);
        Instant until = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(until)) {
            for (var record : reader.poll(Duration.ofMillis(400))) {
                try {
                    if (BrokerLogEnvelope.read(record.key(), record.value()).receiptId().equals(receipt)) return record;
                } catch (PermanentRecordFailure ignored) {
                    // Poison records are independent of the selected test receipt.
                }
            }
        }
        throw new AssertionError("The HTTP receipt was absent from the Kafka topic");
    }

    private Admin admin() {
        return Admin.create(Map.of("bootstrap.servers", "127.0.0.1:19094", "security.protocol","SASL_PLAINTEXT",
                "sasl.mechanism","PLAIN", "sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"devinsight-admin\" password=\""
                        + System.getenv("DEVINSIGHT_STAGE04_ADMIN") + "\";"));
    }

    private long committed(Admin admin, TopicPartition partition) throws Exception {
        var result = admin.listConsumerGroupOffsets(mode.getGroup()).partitionsToOffsetAndMetadata()
                .get(3, TimeUnit.SECONDS).get(partition);
        return result == null ? -1 : result.offset();
    }

    private long end(Admin admin, TopicPartition partition) throws Exception {
        return admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all()
                .get(3, TimeUnit.SECONDS).get(partition).offset();
    }

    private AclBinding dlqRule() {
        return new AclBinding(new ResourcePattern(ResourceType.TOPIC, mode.getDeadLetterTopic(), PatternType.LITERAL),
                new AccessControlEntry("User:devinsight-worker", "*", AclOperation.WRITE, AclPermissionType.ALLOW));
    }

    private record Owner(UUID org, long app, UUID credential, String key) {
        String partitionKey() { return org + "/" + app; }
    }
}
