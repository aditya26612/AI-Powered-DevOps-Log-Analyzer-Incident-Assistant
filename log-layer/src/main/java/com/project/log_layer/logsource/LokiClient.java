package com.project.log_layer.logsource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.dto.request.ingest.ExternalLogEvent;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Component
class LokiClient {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final long NANOS = 1_000_000_000;
    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private final ObjectMapper json;
    private final URI defaultEndpoint;
    private final Set<URI> allowedEndpoints;
    private final UUID organization;
    private final String authorization;
    private final String tenant;

    LokiClient(ObjectMapper json, @Value("${devinsight.loki.url:}") String url,
               @Value("${devinsight.loki.organization:}") String organization,
               @Value("${devinsight.loki.authorization:}") String authorization,
               @Value("${devinsight.loki.tenant:}") String tenant,
               @Value("${devinsight.loki.allowed-urls:}") String allowedUrls) {
        this.json = json;
        this.defaultEndpoint = url.isBlank() ? null : requireOrigin(url);
        this.organization = organization.isBlank() ? null : UUID.fromString(organization);
        this.authorization = authorization;
        this.tenant = tenant;
        var approvals = new HashSet<URI>();
        if (defaultEndpoint != null) approvals.add(defaultEndpoint);
        if (!allowedUrls.isBlank()) {
            for (String entry : allowedUrls.split(",", -1)) approvals.add(requireOrigin(entry));
        }
        this.allowedEndpoints = Set.copyOf(approvals);

        if (allowedEndpoints.isEmpty() != (this.organization == null)
                || (!authorization.isBlank() && (defaultEndpoint == null || !"https".equals(defaultEndpoint.getScheme())))
                || (!tenant.isBlank() && defaultEndpoint == null)
                || !tenant.matches("[A-Za-z0-9._@+!*-]{0,150}")
                || authorization.chars().anyMatch(c -> c < 32 || c >= 127)) {
            throw new IllegalArgumentException("Invalid operator configuration for Loki");
        }
    }

    private static URI origin(String endpoint) {
        if (endpoint == null || endpoint.length() > 512) return null;
        String value = endpoint.strip();
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && (uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    && uri.getPort() != 0 && uri.getPort() <= 65535
                    && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                return URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
            }
        } catch (IllegalArgumentException ignored) {
            // Do not echo the supplied value, which might contain credentials.
        }
        return null;
    }

    private static URI requireOrigin(String value) {
        URI endpoint = origin(value);
        if (endpoint == null) throw new IllegalArgumentException("Invalid operator Loki URL");
        return endpoint;
    }

    UUID organization() {
        return organization;
    }

    boolean canConfigure(UUID org) {
        return org.equals(organization);
    }

    URI approvedEndpoint(UUID org, String input) {
        URI candidate = origin(input);
        if (candidate == null || !canConfigure(org)) return null;
        // Use the operator's configured URI, including its original spelling for event identity.
        return allowedEndpoints.stream().filter(candidate::equals).findFirst().orElse(null);
    }

    URI effectiveEndpoint(UUID org, String saved) {
        if (!canConfigure(org)) return null;
        return saved == null ? defaultEndpoint : approvedEndpoint(org, saved);
    }

    record Line(long nanos, Map<String, String> labels, String text) {}

    static final class Failure extends RuntimeException {
        Failure(String message) {
            super(message);
        }
    }

    List<Line> query(URI endpoint, String label, String value, long start, long end, int limit) {
        String query = "{" + label + "=\"" + value + "\"}";
        if (endpoint == null || !allowedEndpoints.contains(endpoint)) throw new Failure("Loki endpoint is unavailable.");
        URI request = endpoint.resolve("/loki/api/v1/query_range?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start=" + start + "&end=" + end + "&limit=" + limit + "&direction=forward");
        try {
            return parse(fetch(request, endpoint.equals(defaultEndpoint)), label, value, start, end, limit);
        } catch (IOException e) {
            throw new Failure("Could not read Loki response. Check connectivity and try again.");
        }
    }

    private byte[] fetch(URI request, boolean sendCredentials) throws IOException {
        var connection = (HttpURLConnection) request.toURL().openConnection(Proxy.NO_PROXY);
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            if (sendCredentials && !authorization.isBlank()) connection.setRequestProperty("Authorization", authorization);
            if (sendCredentials && !tenant.isBlank()) connection.setRequestProperty("X-Scope-OrgID", tenant);
            int status = connection.getResponseCode();
            if (status != 200) throw new Failure("Loki returned HTTP " + status + ".");
            if (connection.getContentLengthLong() > MAX_BODY_BYTES) throw oversized();
            long deadline = System.nanoTime() + 5 * NANOS;
            try (var input = connection.getInputStream(); var body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1; ) {
                    if (System.nanoTime() > deadline) throw new Failure("Loki response timed out.");
                    if (body.size() + count > MAX_BODY_BYTES) throw oversized();
                    body.write(buffer, 0, count);
                }
                return body.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Failure oversized() {
        return new Failure("Loki response exceeded the size limit. Narrow the stream selector.");
    }

    private List<Line> parse(byte[] response, String label, String selected, long start, long end, int limit)
            throws IOException {
        JsonNode root = json.readTree(response);
        if (root == null || !"success".equals(root.path("status").asText())
                || !"streams".equals(root.path("data").path("resultType").asText())
                || !root.path("data").path("result").isArray()) throw invalid();
        var lines = new ArrayList<Line>();
        for (JsonNode stream : root.path("data").path("result")) {
            if (!stream.path("stream").isObject() || !stream.path("values").isArray()) throw invalid();
            var labels = new TreeMap<String, String>();
            stream.path("stream").fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual()) throw invalid();
                labels.put(entry.getKey(), entry.getValue().textValue());
            });
            if (!selected.equals(labels.get(label))) throw invalid();
            for (JsonNode value : stream.path("values")) {
                if (!value.isArray() || !value.path(0).isTextual() || !value.path(1).isTextual()
                        || lines.size() >= limit) throw invalid();
                long timestamp;
                try {
                    timestamp = Long.parseLong(value.path(0).textValue());
                } catch (NumberFormatException e) {
                    throw invalid();
                }
                if (timestamp < start || timestamp >= end) throw invalid();
                lines.add(new Line(timestamp, labels, value.path(1).textValue()));
            }
        }
        lines.sort(Comparator.comparingLong(Line::nanos));
        return lines;
    }

    private static Failure invalid() {
        return new Failure("Loki returned an invalid log stream response.");
    }

    List<ExternalLogEvent> events(URI endpoint, List<Line> lines, UUID org, long application) {
        var events = new LinkedHashMap<UUID, ExternalLogEvent>();
        for (Line line : lines) {
            try {
                byte[] identity = json.writeValueAsBytes(
                        List.of(endpoint.getScheme(), endpoint.getAuthority(), endpoint.equals(defaultEndpoint) ? tenant : "", org, application,
                                line.nanos(), line.labels(), line.text()));
                UUID id = UUID.nameUUIDFromBytes(identity);
                String timestamp = Instant.ofEpochSecond(line.nanos() / NANOS, line.nanos() % NANOS).toString();
                String message = line.text().replace('\0', ' ');
                if (message.isBlank()) message = "(blank Loki log line)";
                if (message.getBytes(StandardCharsets.UTF_8).length >= 16_384) {
                    message = new String(message.getBytes(StandardCharsets.UTF_8), 0, 16_000, StandardCharsets.UTF_8)
                            + " [truncated]";
                }
                events.putIfAbsent(id, new ExternalLogEvent(id, timestamp, level(line), LogSource.CUSTOM,
                        message, null, null, null, null, null, Map.of()));
            } catch (IOException e) {
                throw invalid();
            }
        }
        return List.copyOf(events.values());
    }

    private LogLevel level(Line line) {
        String level = line.labels().getOrDefault("level", line.labels().get("detected_level"));
        if (level == null && line.text().stripLeading().startsWith("{")) {
            try {
                JsonNode value = json.readTree(line.text());
                if (value != null) level = value.path("level").asText(null);
            } catch (IOException ignored) {
                // Plain text remains a valid log line.
            }
        }
        String normalized = level == null ? "INFO" : level.toUpperCase(Locale.ROOT);
        if ("WARNING".equals(normalized)) normalized = "WARN";
        return LEVELS.contains(normalized) ? LogLevel.valueOf(normalized) : LogLevel.INFO;
    }
}
