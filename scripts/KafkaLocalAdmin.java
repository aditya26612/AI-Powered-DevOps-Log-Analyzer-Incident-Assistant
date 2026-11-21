import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.*;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.resource.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class KafkaLocalAdmin {
    static final String RAW = "devinsight.logs.raw.v1";
    static final String DLQ = "devinsight.logs.dlq.v1";
    static final String GROUP = "devinsight.logs.worker.v1";

    static Map<String,Object> connections() {
        String user = System.getenv("DEVINSIGHT_BROKER_ADMIN_USER");
        String secret = System.getenv("DEVINSIGHT_BROKER_ADMIN_PASSWORD");
        String address = System.getenv("DEVINSIGHT_BROKER_ADDRESS");
        if (address == null || !address.matches("127\\.0\\.0\\.1:(19092|19094)")
                || !"devinsight-admin".equals(user) || secret == null || !secret.matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("Protected local admin configuration is required");
        }
        return Map.of("bootstrap.servers", address, "security.protocol", "SASL_PLAINTEXT", "sasl.mechanism", "PLAIN",
                "sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                        + user + "\" password=\"" + secret + "\";",
                "request.timeout.ms", 1500, "default.api.timeout.ms", 2500);
    }

    public static void main(String[] args) {
        Admin admin = null;
        try {
            admin = Admin.create(connections());
            if (args.length == 1 && args[0].equals("initialize")) create(admin);
            else if (args.length != 1 || !args[0].equals("status")) throw new IllegalArgumentException();
            status(admin);
        } catch (Exception e) {
            if (Boolean.getBoolean("devinsight.kafka.debugStatus")) {
                System.err.println("Kafka admin failed: " + e.getClass().getSimpleName()
                        + (e.getCause() == null ? "" : "/" + e.getCause().getClass().getSimpleName()));
            }
            System.exit(1);
        } finally {
            if (admin != null) admin.close(Duration.ofSeconds(1));
        }
    }

    private static void create(Admin admin) throws Exception {
        topic(admin, RAW, 3, 32 * 1024 * 1024, 86_400_000);
        topic(admin, DLQ, 1, 16 * 1024 * 1024, 604_800_000);
        var rules = new ArrayList<AclBinding>();
        allow(rules, "devinsight-writer", ResourceType.TOPIC, RAW, AclOperation.WRITE, AclOperation.DESCRIBE);
        allow(rules, "devinsight-writer", ResourceType.GROUP, GROUP, AclOperation.DESCRIBE);
        allow(rules, "devinsight-worker", ResourceType.TOPIC, RAW, AclOperation.READ, AclOperation.DESCRIBE);
        allow(rules, "devinsight-worker", ResourceType.TOPIC, DLQ, AclOperation.WRITE, AclOperation.DESCRIBE);
        allow(rules, "devinsight-worker", ResourceType.GROUP, GROUP, AclOperation.READ, AclOperation.DESCRIBE);
        allow(rules, "devinsight-replay", ResourceType.TOPIC, RAW, AclOperation.READ, AclOperation.WRITE, AclOperation.DESCRIBE);
        allow(rules, "devinsight-replay", ResourceType.TOPIC, DLQ, AclOperation.READ, AclOperation.DESCRIBE);
        for (String user : List.of("devinsight-writer", "devinsight-worker", "devinsight-replay")) {
            allow(rules, user, ResourceType.CLUSTER, Resource.CLUSTER_NAME, AclOperation.IDEMPOTENT_WRITE);
        }
        admin.createAcls(rules).all().get(3, TimeUnit.SECONDS);
    }

    private static void topic(Admin admin, String name, int partitions, int bytes, int millis) throws Exception {
        var topic = new NewTopic(name, partitions, (short) 1).configs(Map.of(
                "retention.bytes", Integer.toString(bytes), "retention.ms", Integer.toString(millis),
                "cleanup.policy", "delete", "max.message.bytes", "131072", "segment.bytes", "4194304",
                "segment.ms", "300000", "min.insync.replicas", "1"));
        try {
            admin.createTopics(List.of(topic)).all().get(3, TimeUnit.SECONDS);
        } catch (ExecutionException exists) {
            if (!(exists.getCause() instanceof TopicExistsException)) throw exists;
        }
    }

    private static void allow(List<AclBinding> rules, String user, ResourceType type, String name, AclOperation... ops) {
        for (var operation : ops) {
            rules.add(new AclBinding(new ResourcePattern(type, name, PatternType.LITERAL),
                    new AccessControlEntry("User:" + user, "*", operation, AclPermissionType.ALLOW)));
        }
    }

    private static void status(Admin admin) throws Exception {
        String cluster = admin.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
        var descriptions = admin.describeTopics(List.of(RAW, DLQ)).allTopicNames().get(3, TimeUnit.SECONDS);
        var raw = descriptions.get(RAW).partitions();
        var dlq = descriptions.get(DLQ).partitions();
        if (raw.size() != 3 || dlq.size() != 1 || cluster == null || cluster.isBlank()
                || descriptions.values().stream().flatMap(t -> t.partitions().stream())
                .anyMatch(p -> p.leader() == null || p.leader().id() < 0 || p.replicas().size() != 1 || p.isr().size() != 1)) {
            throw new IllegalStateException("Kafka topic layout or quorum is unhealthy");
        }
        var latest = new HashMap<TopicPartition, OffsetSpec>();
        var earliest = new HashMap<TopicPartition, OffsetSpec>();
        descriptions.forEach((t, info) -> info.partitions().forEach(p -> {
            var tp = new TopicPartition(t, p.partition());
            earliest.put(tp, OffsetSpec.earliest());
            latest.put(tp, OffsetSpec.latest());
        }));
        var entries = admin.listOffsets(latest).all().get(3, TimeUnit.SECONDS);
        var beginnings = admin.listOffsets(earliest).all().get(3, TimeUnit.SECONDS);
        var positions = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(3, TimeUnit.SECONDS);
        long lag = 0;
        long poison = 0;
        for (var item : entries.entrySet()) {
            long start = beginnings.get(item.getKey()).offset();
            var committed = positions.get(item.getKey());
            if (item.getKey().topic().equals(RAW)) {
                long offset = committed == null ? start : Math.max(start, committed.offset());
                lag += Math.max(0, item.getValue().offset() - offset);
            } else {
                poison += item.getValue().offset() - start;
            }
        }
        System.out.printf("{\"ready\":true,\"clusterId\":\"%s\",\"rawPartitions\":3,\"dlqPartitions\":1,\"lag\":%d,\"dlqRecords\":%d}%n",
                cluster, lag, poison);
    }
}
