package com.project.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

final class BatchCollector implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String PATH = "/api/v1/logs/batch";
    static final String ASYNC_PATH = "/api/v2/logs/batch";
    private static final Pattern RECEIPT = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final List<String> LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private static final Set<String> ENVIRONMENTS = Set.of("DEVELOPMENT", "TESTING", "QA", "STAGING", "PRODUCTION");
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504);
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY = Pattern.compile("dvi_[A-Za-z0-9_-]{16,}");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|authorization|api[-_ ]?key|cookie)([\"']?\\s*[:=]\\s*)"
                    + "(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;]+)");
    private static final Pattern USERINFO = Pattern.compile("(?i)(\\b[a-z][a-z0-9+.-]*://)[^/\\s@]+:[^/\\s@]+@");
    private static final Pattern LOG_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private final String key;
    private final boolean asynchronous;
    private final int success;
    private final URI endpoint;
    private final byte[] prefix;
    private final HttpClient client;
    private final Settings settings;
    private final ArrayDeque<Frame> queue = new ArrayDeque<>();
    private final Thread worker;
    private volatile long deadline;
    private int inFlight;
    private long captured, enqueued, requests, attemptedEvents, acknowledged, duplicates;
    private long retries, failedBatches, rejected, dropped, peakDepth;
    private String lastSuccess, lastFailure;

    record Stats(long captured, long enqueued, long requests, long attemptedEvents, long acknowledged,
                 long duplicates, long retries, long failedBatches, long rejected, long dropped,
                 int queueDepth, long peakDepth, int inFlight, String lastSuccess, String lastFailure,
                 long brokerAccepted, long persisted) {}

    record Settings(int count, int bytes, int capacity, int flush, int connect, int timeout, int attempts,
                    int shutdown, String minimum, boolean redact) {
        Settings {
            if (count < 1 || count > 10 || bytes < 1024 || bytes > 65536 || capacity < 1 || capacity > 1024
                    || flush < 10 || flush > 5000 || connect < 50 || connect > 10000
                    || timeout < 50 || timeout > 60000 || attempts < 1 || attempts > 8
                    || shutdown < 50 || shutdown > 30000 || !LEVELS.contains(minimum)) {
                throw new IllegalArgumentException("Invalid DevInsight collector settings");
            }
        }

        static Settings fromEnvironment() {
            return new Settings(number("BATCH_COUNT", 10), number("BATCH_BYTES", 65536),
                    number("QUEUE_CAPACITY", 256), number("FLUSH_MS", 250), number("CONNECT_TIMEOUT_MS", 1500),
                    number("REQUEST_TIMEOUT_MS", 15000), number("MAX_ATTEMPTS", 4), number("SHUTDOWN_MS", 5000),
                    System.getenv().getOrDefault("DEVINSIGHT_MIN_LEVEL", "INFO").toUpperCase(java.util.Locale.ROOT),
                    !"false".equalsIgnoreCase(System.getenv("DEVINSIGHT_REDACT")));
        }

        private static int number(String name, int fallback) {
            try {
                return Integer.parseInt(System.getenv().getOrDefault("DEVINSIGHT_" + name, Integer.toString(fallback)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid DevInsight collector settings");
            }
        }
    }

    private record Frame(String id, byte[] data, long submitted) {}
    private record Reply(int status, byte[] body, String retryAfter) {}

    BatchCollector(String endpoint, String key, String application, String environment, Settings settings) {
        this(endpoint, key, application, environment, settings, false);
    }

    BatchCollector(String endpoint, String key, String application, String environment, Settings settings,
                   boolean asynchronous) {
        this.asynchronous = asynchronous;
        this.success = asynchronous ? 202 : 200;
        String requiredPath = asynchronous ? ASYNC_PATH : PATH;
        if (key == null || !key.matches("[\\x21-\\x7e]{16,160}")) {
            throw new IllegalArgumentException("A valid DEVINSIGHT_API_KEY is required");
        }
        if (application == null || application.isBlank() || application.length() > 100
                || environment == null || !ENVIRONMENTS.contains(environment)) {
            throw new IllegalArgumentException("DevInsight application name and environment are required");
        }
        try {
            this.endpoint = URI.create(endpoint);
            String host = this.endpoint.getHost();
            boolean local = host != null && List.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(java.util.Locale.ROOT));
            if (host == null || this.endpoint.getUserInfo() != null || this.endpoint.getQuery() != null
                    || this.endpoint.getFragment() != null || !requiredPath.equals(this.endpoint.getPath())
                    || !("https".equals(this.endpoint.getScheme()) || "http".equals(this.endpoint.getScheme()) && local)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Use an HTTPS DevInsight batch URL or a loopback HTTP URL");
        }
        this.key = key;
        this.settings = settings;
        this.prefix = utf8("{\"applicationName\":" + JSON.valueToTree(application) + ",\"environment\":"
                + JSON.valueToTree(environment) + ",\"events\":[");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.connect()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        worker = Thread.ofPlatform().daemon().name("devinsight-java-sender").start(this::run);
    }

    void capture(String level, Instant time, String message, String exception, String logger, String thread) {
        capture(level,time,message,exception,logger,thread,null,null);
    }

    void capture(String level, Instant time, String message, String exception, String logger, String thread,
                 String traceparent, String origin) {
        if (LEVELS.indexOf(level) < LEVELS.indexOf(settings.minimum())
                || logger != null && logger.startsWith("devinsight.collector")) {
            return;
        }
        synchronized (queue) {
            captured++;
            if (deadline != 0 || queue.size() >= settings.capacity()) {
                dropped++;
                return;
            }
        }
        var id = UUID.randomUUID().toString();
        var event = new LinkedHashMap<String, String>();
        event.put("eventId", id);
        event.put("timestamp", time.toString());
        event.put("level", level);
        event.put("source", "SPRING_BOOT");
        String text = clean(message, 8192);
        event.put("message", text.isBlank() ? "(empty log message)" : text);
        if (exception != null) event.put("exception", clean(exception, 4096));
        if (logger != null) event.put("loggerName", clean(logger, 255));
        if (thread != null) event.put("threadName", clean(thread, 100));
        if (traceparent != null && traceparent.length() == 55
                && traceparent.matches("00-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}")
                && !traceparent.substring(3,35).equals("0".repeat(32))
                && !traceparent.substring(36,52).equals("0".repeat(16))) {
            event.put("traceparent", traceparent);
            event.put("traceOrigin", origin != null && List.of("INBOUND_REPORTED","LOCAL_ROOT_REPORTED","EXPLICIT")
                    .contains(origin) ? origin : "COLLECTOR_REPORTED");
        }

        byte[] encoded = utf8(JSON.valueToTree(event).toString());
        synchronized (queue) {
            if (prefix.length + encoded.length + 2 > settings.bytes()) {
                rejected++;
            } else if (deadline != 0 || queue.size() >= settings.capacity()) {
                dropped++;
            } else {
                queue.add(new Frame(id, encoded, System.nanoTime()));
                enqueued++;
                peakDepth = Math.max(peakDepth, queue.size());
                queue.notifyAll();
            }
        }
    }

    private String clean(String value, int maximum) {
        String text = (value == null ? "" : value).replace(key, "[REDACTED]");
        if (settings.redact()) {
            text = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
            text = KEY.matcher(text).replaceAll("[REDACTED]");
            text = ASSIGNMENT.matcher(text).replaceAll("$1$2[REDACTED]");
            text = USERINFO.matcher(text).replaceAll("$1[REDACTED]@");
        }
        byte[] data = utf8(text.replace('\0', '\uFFFD'));
        int end = Math.min(data.length, maximum);
        while (end < data.length && (data[end] & 0xc0) == 0x80) end--;
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    Stats stats() {
        synchronized (queue) {
            return new Stats(captured, enqueued, requests, attemptedEvents, acknowledged, duplicates, retries,
                    failedBatches, rejected, dropped, queue.size(), peakDepth, inFlight, lastSuccess, lastFailure,
                    asynchronous ? acknowledged : 0, asynchronous ? 0 : acknowledged);
        }
    }

    private List<Frame> take() throws InterruptedException {
        synchronized (queue) {
            while (true) {
                if (deadline != 0 && (queue.isEmpty() || remaining() <= 0)) return null;
                if (queue.isEmpty()) {
                    queue.wait();
                    continue;
                }
                long wait = queue.getFirst().submitted() + TimeUnit.MILLISECONDS.toNanos(settings.flush()) - System.nanoTime();
                if (deadline == 0 && queue.size() < settings.count() && wait > 0) {
                    TimeUnit.NANOSECONDS.timedWait(queue, wait);
                    continue;
                }
                var batch = new ArrayList<Frame>();
                int bytes = prefix.length + 2;
                while (!queue.isEmpty() && batch.size() < settings.count()) {
                    int next = bytes + queue.getFirst().data().length + (batch.isEmpty() ? 0 : 1);
                    if (next > settings.bytes()) break;
                    batch.add(queue.removeFirst());
                    bytes = next;
                }
                inFlight = batch.size();
                return batch;
            }
        }
    }

    private void run() {
        try {
            List<Frame> batch;
            while ((batch = take()) != null) {
                deliver(batch);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (queue) {
                dropped += inFlight + queue.size();
                inFlight = 0;
                queue.clear();
            }
        }
    }

    private void deliver(List<Frame> batch) throws InterruptedException {
        var buffer = new ByteArrayOutputStream();
        buffer.writeBytes(prefix);
        for (int i = 0; i < batch.size(); i++) {
            if (i != 0) buffer.write(',');
            buffer.writeBytes(batch.get(i).data());
        }
        buffer.writeBytes(utf8("]}"));
        byte[] body = buffer.toByteArray();
        String failure = "retry_exhausted";
        for (int attempt = 0; attempt < settings.attempts(); attempt++) {
            if (remaining() <= 0) {
                countDropped(batch.size());
                return;
            }
            synchronized (queue) { requests++; attemptedEvents += batch.size(); }
            Reply reply = send(body);
            if (reply.status() == success) {
                int repeated = acknowledgements(reply.body(), batch, asynchronous);
                if (repeated >= 0) {
                    synchronized (queue) {
                        acknowledged += batch.size();
                        inFlight -= batch.size();
                        duplicates += repeated;
                        lastSuccess = Instant.now().toString();
                    }
                    return;
                }
                failure = "invalid_ack";
            } else if (reply.status() == 413 && batch.size() > 1) {
                int middle = batch.size() / 2;
                deliver(batch.subList(0, middle));
                deliver(batch.subList(middle, batch.size()));
                return;
            } else {
                failure = reply.status() == 0 ? "network" : "http_" + reply.status();
                if (reply.status() != 0 && !RETRYABLE.contains(reply.status())) break;
            }
            if (attempt + 1 == settings.attempts()) break;
            long jitter = ThreadLocalRandom.current().nextLong(100, Math.min(4000, 250L << attempt) + 1);
            if (!sleep(Math.max(jitter, retryAfter(reply.retryAfter())))) {
                countDropped(batch.size());
                return;
            }
            synchronized (queue) { retries++; }
        }
        synchronized (queue) {
            failedBatches++;
            rejected += batch.size();
            inFlight -= batch.size();
            lastFailure = failure;
        }
    }

    private void countDropped(int amount) {
        synchronized (queue) {
            dropped += amount;
            inFlight -= amount;
        }
    }

    private long remaining() {
        return deadline == 0 ? Long.MAX_VALUE : deadline - System.nanoTime();
    }

    private boolean sleep(long millis) throws InterruptedException {
        long wake = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        synchronized (queue) {
            while (true) {
                if (deadline != 0 && wake >= deadline) return false;
                long wait = wake - System.nanoTime();
                if (wait <= 0) return true;
                TimeUnit.NANOSECONDS.timedWait(queue, wait);
            }
        }
    }

    private Reply send(byte[] body) throws InterruptedException {
        long wait = Math.min(remaining(), TimeUnit.MILLISECONDS.toNanos(settings.timeout()));
        if (wait <= 0) return new Reply(0, null, null);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofNanos(wait))
                .header("Content-Type", "application/json")
                .header("X-API-Key", key)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var pending = client.sendAsync(request, response -> new BoundedBody());
        try {
            var response = pending.get(wait, TimeUnit.NANOSECONDS);
            return new Reply(response.statusCode(), response.body(), response.headers().firstValue("Retry-After").orElse(null));
        } catch (ExecutionException | TimeoutException e) {
            return new Reply(0, null, null);
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > 8192 - bytes.size()) {
                    result.complete(new byte[0]);
                    subscription.cancel();
                    return;
                }
                byte[] piece = new byte[chunk.remaining()];
                chunk.get(piece);
                bytes.writeBytes(piece);
            }
            subscription.request(1);
        }

        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }

    private static int acknowledgements(byte[] body, List<Frame> batch, boolean asynchronous) {
        try {
            JsonNode response = JSON.readTree(body);
            if (asynchronous && (!"BROKER_ACCEPTED".equals(response.path("acknowledgement").asText())
                    || !"ASYNCHRONOUS".equals(response.path("processingState").asText())
                    || !RECEIPT.matcher(response.path("receiptId").asText()).matches())) return -1;
            JsonNode rows = response.path("events");
            if (!rows.isArray() || rows.size() != batch.size()) return -1;
            int matches = 0;
            for (int i = 0; i < batch.size(); i++) {
                JsonNode row = rows.get(i);
                String status = row.path("status").asText();
                if (!batch.get(i).id().equals(row.path("eventId").asText())) return -1;
                if (asynchronous) {
                    if (!"BROKER_ACCEPTED".equals(status) || row.has("logId")) return -1;
                } else {
                    if (!row.path("logId").isTextual() || !LOG_ID.matcher(row.path("logId").asText()).matches()) return -1;
                    if ("DUPLICATE".equals(status)) matches++;
                    else if (!"ACCEPTED".equals(status)) return -1;
                }
            }
            return matches;
        } catch (Exception failure) {
            return -1;
        }
    }

    private static long retryAfter(String value) {
        if (value == null) return 0;
        try {
            long millis = value.matches("\\d{1,12}") ? Long.parseLong(value) * 1000
                    : Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
            return Math.max(0, Math.min(60000, millis));
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    @Override public void close() {
        synchronized (queue) {
            if (deadline == 0) {
                deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.shutdown());
                queue.notifyAll();
            }
        }
        try {
            long wait = remaining();
            if (wait > 0) TimeUnit.NANOSECONDS.timedJoin(worker, wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) worker.interrupt();
        client.shutdownNow();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
