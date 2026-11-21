package com.project.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BatchCollectorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void forwardsValidReportedTraceAndOmitsInvalidContext() throws Exception {
        var events = new CopyOnWriteArrayList<JsonNode>();
        String parent = "00-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01";
        try (var receiver = new Receiver(exchange -> {
            var batch = JSON.readTree(exchange.getRequestBody().readAllBytes());
            batch.path("events").forEach(events::add);
            ack(exchange, batch, "ACCEPTED");
        }); var collector = receiver.collector(4, 65536, 12)) {
            collector.capture("ERROR", Instant.now(), "valid", null, "example", null, parent, "INBOUND_REPORTED");
            collector.capture("ERROR", Instant.now(), "invalid", null, "example", null, "fake", "EXPLICIT");
            collector.close();
            assertEquals(2, events.size());
            assertEquals(parent, events.getFirst().path("traceparent").asText());
            assertEquals("INBOUND_REPORTED", events.getFirst().path("traceOrigin").asText());
            assertFalse(events.getLast().has("traceparent"));
        }
    }

    @Test
    void lostResponseRetriesIdenticalBytesAndValidatesAcknowledgement() throws Exception {
        var count = new AtomicInteger();
        var bodies = new CopyOnWriteArrayList<JsonNode>();
        try (var receiver = new Receiver(exchange -> {
            var batch = JSON.readTree(exchange.getRequestBody().readAllBytes());
            bodies.add(batch);
            if (count.incrementAndGet() != 1) {
                ack(exchange, batch, "DUPLICATE");
            }
        }); var collector = receiver.collector(4, 1024, 12)) {
            collector.capture("ERROR", Instant.now(), "password=fixture-secret", "Exception: fixture",
                    "example.logger", "example-thread");
            collector.close();
            assertEquals(2, count.get());
            assertEquals(bodies.get(0), bodies.get(1));
            assertFalse(bodies.get(0).toString().contains("fixture-secret"));
            assertEquals(1, collector.stats().duplicates());
            assertEquals(1, collector.stats().retries());
        }
    }

    @Test
    void queueIsBoundedAndEachBatchRespectsByteAndCountLimits() throws Exception {
        var sizes = new CopyOnWriteArrayList<Integer>();
        var counts = new CopyOnWriteArrayList<Integer>();
        try (var receiver = new Receiver(exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            JsonNode batch = JSON.readTree(bytes);
            sizes.add(bytes.length);
            counts.add(batch.get("events").size());
            ack(exchange, batch, "ACCEPTED");
        }); var collector = receiver.collector(2, 1024, 4)) {
            var callers = new ArrayList<Thread>();
            for (int n = 0; n < 12; n++) {
                callers.add(Thread.startVirtualThread(() ->
                        collector.capture("INFO", Instant.now(), "λ".repeat(280), null, "example", null)));
            }
            for (Thread caller : callers) caller.join();
            collector.close();
            var stats = collector.stats();
            assertEquals(12, stats.captured());
            assertTrue(stats.peakDepth() <= 4 && stats.dropped() > 0);
            assertEquals(12, stats.acknowledged() + stats.dropped());
            assertTrue(sizes.stream().allMatch(size -> size <= 1024));
            assertTrue(counts.stream().allMatch(count -> count <= 2));
        }
    }

    @Test
    void permanentAuthenticationFailureAndRecursionAreAccounted() throws Exception {
        var requests = new AtomicInteger();
        try (var receiver = new Receiver(exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
        }); var collector = receiver.collector(4, 65536, 4)) {
            collector.capture("WARN", Instant.now(), "internal", null, "devinsight.collector.status", null);
            collector.capture("DEBUG", Instant.now(), "trace", null, "example", null);
            collector.capture("INFO", Instant.now(), "event", null, "example", null);
            collector.close();
            assertEquals(1, requests.get());
            assertEquals(1, collector.stats().rejected());
            assertEquals(0, collector.stats().retries());
            assertEquals("http_401", collector.stats().lastFailure());
        }
    }

    @Test
    void asynchronousModeValidatesBrokerAcknowledgementAndCountsSeparately() throws Exception {
        var attempts = new AtomicInteger();
        var bodies = new CopyOnWriteArrayList<JsonNode>();
        try (var receiver = new Receiver(exchange -> {
            var batch = JSON.readTree(exchange.getRequestBody().readAllBytes());
            bodies.add(batch);
            var reply = JSON.createObjectNode();
            if (attempts.incrementAndGet() != 1) {
                reply.put("receiptId", "12345678-1234-1234-1234-123456789abc");
                reply.put("acknowledgement", "BROKER_ACCEPTED");
                reply.put("processingState", "ASYNCHRONOUS");
                reply.putArray("events").addObject()
                        .put("eventId", batch.get("events").get(0).get("eventId").asText())
                        .put("status", "BROKER_ACCEPTED");
            }
            byte[] body = JSON.writeValueAsBytes(reply);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
        }, BatchCollector.ASYNC_PATH); var collector = receiver.collector(4, 65536, 12, true)) {
            collector.capture("INFO", Instant.now(), "sample", null, "example", null);
            collector.close();
            assertEquals(1, collector.stats().brokerAccepted());
            assertEquals(0, collector.stats().persisted());
            assertEquals(1, collector.stats().retries());
            assertEquals(bodies.get(0), bodies.get(1));
        }
    }

    private static void ack(HttpExchange exchange, JsonNode batch, String status) throws IOException {
        var reply = JSON.createArrayNode();
        for (JsonNode item : batch.get("events")) {
            reply.addObject().put("eventId", item.get("eventId").asText()).put("logId", "1").put("status", status);
        }
        byte[] bytes = JSON.writeValueAsBytes(JSON.createObjectNode().set("events", reply));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class Receiver implements AutoCloseable {
        private final HttpServer server;

        Receiver(Handler handler) throws IOException {
            this(handler, BatchCollector.PATH);
        }

        Receiver(Handler handler, String path) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, exchange -> {
                try (exchange) {
                    handler.handle(exchange);
                }
            });
            server.start();
        }

        BatchCollector collector(int count, int bytes, int capacity) {
            return collector(count, bytes, capacity, false);
        }

        BatchCollector collector(int count, int bytes, int capacity, boolean asynchronous) {
            var settings = new BatchCollector.Settings(count, bytes, capacity, 25, 250, 800, 3, 2500, "INFO", true);
            String path = asynchronous ? BatchCollector.ASYNC_PATH : BatchCollector.PATH;
            return new BatchCollector("http://127.0.0.1:" + server.getAddress().getPort() + path,
                    "synthetic-key-for-local-test", "fixture", "TESTING", settings, asynchronous);
        }

        @Override public void close() { server.stop(0); }
    }
}
