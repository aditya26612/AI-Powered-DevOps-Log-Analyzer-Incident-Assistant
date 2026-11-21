import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class KafkaLocalReplay {
    private static final String RAW = "devinsight.logs.raw.v1";
    private static final String DLQ = "devinsight.logs.dlq.v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        try {
            if (args.length != 4 || !List.of("preview", "replay").contains(args[0])) {
                throw new IllegalArgumentException("Choose preview or replay for exactly one record");
            }
            long offset = Long.parseLong(args[1]);
            UUID organization = UUID.fromString(args[2]);
            long application = Long.parseLong(args[3]);
            if (offset < 0 || application < 1) throw new IllegalArgumentException("Invalid record or application ID");
            String scopedKey = organization + "/" + application;
            Map<String, Object> connection = connection();
            var settings = new HashMap<>(connection);
            settings.put("enable.auto.commit", false);
            settings.put("auto.offset.reset", "none");
            settings.put("max.poll.records", 1);
            settings.put("allow.auto.create.topics", false);
            try (var reader = new KafkaConsumer<>(settings, new StringDeserializer(), new ByteArrayDeserializer())) {
                var receipt = JSON.readTree(read(reader, new TopicPartition(DLQ, 0), offset).value());
                if (receipt.path("version").asInt() != 1 || !RAW.equals(receipt.path("topic").asText())
                        || !organization.toString().equals(receipt.path("organizationId").asText())
                        || !Long.toString(application).equals(receipt.path("applicationId").asText())) {
                    throw new IllegalStateException("The dead letter has no matching replayable tenant identity");
                }
                int sourcePartition = receipt.path("partition").asInt(-1);
                long sourceOffset = receipt.path("offset").asLong(-1);
                if (sourcePartition < 0 || sourcePartition > 2 || sourceOffset < 0) {
                    throw new IllegalStateException("Invalid source location");
                }
                var raw = read(reader, new TopicPartition(RAW, sourcePartition), sourceOffset);
                if (!scopedKey.equals(raw.key()) || raw.value() == null || raw.value().length > 98304) {
                    throw new IllegalStateException("The retained record does not belong to the selected application");
                }
                JsonNode envelope = JSON.readTree(raw.value());
                if (envelope.path("version").asInt() != 1
                        || !organization.toString().equals(envelope.path("organizationId").asText())
                        || envelope.path("applicationId").asLong() != application
                        || !receipt.path("receiptId").asText().equals(envelope.path("receiptId").asText())
                        || !envelope.path("events").isArray() || envelope.path("events").isEmpty()
                        || envelope.path("events").size() > 10) {
                    throw new IllegalStateException("The retained record is not a recognized application batch");
                }
                if (args[0].equals("replay")) {
                    publish(connection, raw);
                    System.out.println("One scoped batch was broker-acknowledged with its original event IDs.");
                } else {
                    System.out.println("Preview verified one scoped retained batch with " + envelope.path("events").size()
                            + " event(s). No record was published.");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Only safe diagnostics, never deserializer messages or raw records.
            System.err.println("Replay refused. Check the exact offset, tenant, application, and source retention.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Replay failed without confirmed publication. Keep the original event IDs.");
            System.exit(1);
        }
    }

    private static Map<String, Object> connection() {
        String address = System.getenv("DEVINSIGHT_BROKER_ADDRESS");
        String secret = System.getenv("DEVINSIGHT_BROKER_REPLAY_PASSWORD");
        if (address == null || !address.matches("127\\.0\\.0\\.1:(19092|19094)")
                || secret == null || !secret.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
        return Map.of("bootstrap.servers", address, "security.protocol", "SASL_PLAINTEXT", "sasl.mechanism", "PLAIN",
                "sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required "
                        + "username=\"devinsight-replay\" password=\"" + secret + "\";",
                "request.timeout.ms", 2000, "default.api.timeout.ms", 3000);
    }

    private static ConsumerRecord<String, byte[]> read(KafkaConsumer<String, byte[]> reader, TopicPartition where, long offset) {
        reader.assign(List.of(where));
        reader.seek(where, offset);
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            for (var record : reader.poll(Duration.ofMillis(400))) {
                if (record.offset() == offset) return record;
                if (record.offset() > offset) throw new IllegalStateException("Record outside retention");
            }
        }
        throw new IllegalStateException("Record not found");
    }

    private static void publish(Map<String, Object> connection, ConsumerRecord<String, byte[]> raw) throws Exception {
        var config = new HashMap<>(connection);
        config.remove("default.api.timeout.ms");
        config.put("acks", "all");
        config.put("enable.idempotence", true);
        config.put("max.block.ms", 2000);
        config.put("delivery.timeout.ms", 5000);
        try (var writer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer())) {
            writer.send(new ProducerRecord<>(RAW, raw.key(), raw.value())).get(6, TimeUnit.SECONDS);
        }
    }
}
