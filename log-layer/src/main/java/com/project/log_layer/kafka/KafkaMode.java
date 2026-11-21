package com.project.log_layer.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties("devinsight.kafka")
public final class KafkaMode {
    private boolean producerEnabled = true;
    private boolean consumerEnabled = true;
    private String bootstrap = "127.0.0.1:19092";
    private String securityProtocol = "SASL_PLAINTEXT";
    private String mechanism = "PLAIN";
    private String rawTopic = "devinsight.logs.raw.v1";
    private String deadLetterTopic = "devinsight.logs.dlq.v1";
    private String group = "devinsight.logs.worker.v1";
    private String producerUsername = "";
    private String producerPassword = "";
    private String consumerUsername = "";
    private String consumerPassword = "";
    private int concurrency = 1;
    private int publishTimeoutMs = 5000;
    private int maxBacklog = 200;
    private int attempts = 3;
    private int pauseMs = 5000;

    void validate() {
        if ((!producerEnabled && !consumerEnabled) || concurrency < 1 || concurrency > 3
                || publishTimeoutMs < 1000 || publishTimeoutMs > 15000 || maxBacklog < 1 || maxBacklog > 100000
                || attempts < 1 || attempts > 4 || pauseMs < 1000 || pauseMs > 60000
                || !safeName(rawTopic) || !safeName(deadLetterTopic) || !safeName(group)
                || rawTopic.equals(deadLetterTopic)
                || !List.of("SASL_PLAINTEXT", "SASL_SSL").contains(securityProtocol)
                || !List.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512").contains(mechanism)) {
            throw new IllegalStateException("Invalid Kafka mode configuration");
        }
        for (String address : bootstrap.split(",", -1)) {
            String server = address.strip();
            if (!server.matches("(?:[a-zA-Z0-9.-]+|\\[[0-9a-fA-F:]+]):[1-9][0-9]{0,4}")
                    || Integer.parseInt(server.substring(server.lastIndexOf(':') + 1)) > 65535
                    || ("SASL_PLAINTEXT".equals(securityProtocol)
                        && !server.matches("(?:127\\.0\\.0\\.1|localhost|\\[::1]):[0-9]+"))) {
                throw new IllegalStateException("Kafka bootstrap must use TLS outside loopback");
            }
        }
        if (producerEnabled) checkedCredentials(producerUsername, producerPassword);
        if (consumerEnabled) checkedCredentials(consumerUsername, consumerPassword);
    }

    private static boolean safeName(String name) {
        return name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}");
    }

    private static void checkedCredentials(String user, String pass) {
        if (!safeName(user) || pass.length() < 16 || pass.length() > 256
                || pass.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Kafka credentials require a valid username and protected password");
        }
    }

    Map<String, Object> connection(boolean consumer) {
        String user = consumer ? consumerUsername : producerUsername;
        String pass = consumer ? consumerPassword : producerPassword;
        String module = mechanism.equals("PLAIN")
                ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                : "org.apache.kafka.common.security.scram.ScramLoginModule";

        return Map.of("bootstrap.servers", bootstrap, "security.protocol", securityProtocol,
                "sasl.mechanism", mechanism, "sasl.jaas.config", module + " required username="
                + quote(user) + " password=" + quote(pass) + ";");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public boolean isProducerEnabled() { return producerEnabled; }
    public void setProducerEnabled(boolean value) { producerEnabled = value; }
    public boolean isConsumerEnabled() { return consumerEnabled; }
    public void setConsumerEnabled(boolean value) { consumerEnabled = value; }
    public void setBootstrap(String value) { bootstrap = value; }
    public void setSecurityProtocol(String value) { securityProtocol = value; }
    public void setMechanism(String value) { mechanism = value; }
    public String getRawTopic() { return rawTopic; }
    public void setRawTopic(String value) { rawTopic = value; }
    public String getDeadLetterTopic() { return deadLetterTopic; }
    public void setDeadLetterTopic(String value) { deadLetterTopic = value; }
    public String getGroup() { return group; }
    public void setGroup(String value) { group = value; }
    public void setProducerUsername(String value) { producerUsername = value; }
    public void setProducerPassword(String value) { producerPassword = value; }
    public void setConsumerUsername(String value) { consumerUsername = value; }
    public void setConsumerPassword(String value) { consumerPassword = value; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int value) { concurrency = value; }
    public int getPublishTimeoutMs() { return publishTimeoutMs; }
    public void setPublishTimeoutMs(int value) { publishTimeoutMs = value; }
    public int getMaxBacklog() { return maxBacklog; }
    public void setMaxBacklog(int value) { maxBacklog = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public int getPauseMs() { return pauseMs; }
    public void setPauseMs(int value) { pauseMs = value; }
}
