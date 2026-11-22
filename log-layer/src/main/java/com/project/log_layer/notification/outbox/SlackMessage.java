package com.project.log_layer.notification.outbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class SlackMessage {
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)\\b(password|passwd|token|secret|api[-_ ]?key|authorization|cookie)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|\\S+)"
            + "|\\bBearer\\s+\\S+|\\bdvi_[A-Za-z0-9_-]{12,}|\\bxox[a-z]-[A-Za-z0-9-]+");
    private static final Pattern LINK = Pattern.compile(
            "(?i)\\b(?:[a-z][a-z0-9+.-]*://|www\\.)\\S+|\\b(?:[a-z0-9-]+\\.)+(?:com|net|org|io|dev|app|local)(?:/\\S*)?\\b");
    private static final Pattern CONTROLS = Pattern.compile("[\\p{Cc}\\p{Cf}]+");

    record Evidence(UUID reference, UUID incident, long log, String application, String environment, int severity,
                    String incidentStatus, String first, String last, long events, String timestamp,
                    String summary, String cause, String recommendation, boolean multiApplication) {}

    private SlackMessage() {}

    static Map<String, Object> payload(Evidence item) {
        String severity = switch (item.severity) {
            case 3 -> "CRITICAL";
            case 2 -> "HIGH";
            default -> "MEDIUM";
        };
        String reference = (item.incident == null ? "Log " + item.log : "Incident " + item.incident);
        String summary = item.multiApplication
                ? "This incident spans applications. Review the diagnosis in DevInsight."
                : "Summary: " + safe(item.summary, 420) + "\nProbable cause: " + safe(item.cause, 260)
                + "\nSuggested action: " + safe(item.recommendation, 260);
        String timing = item.incident == null ? safe(item.timestamp, 60)
                : safe(item.first, 60) + " to " + safe(item.last, 60) + " · " + item.events + " events";
        String details = reference + "\nApplication: " + safe(item.application, 100) + " · "
                + safe(item.environment, 24) + "\nStatus: " + safe(item.incidentStatus, 24)
                + "\nOccurrence: " + timing + "\nDelivery reference: " + item.reference;
        return Map.of("text", "DevInsight " + severity + " " + reference + " · " + item.reference,
                "unfurl_links", false, "unfurl_media", false, "blocks", List.of(
                        Map.of("type", "header", "text", plain("DevInsight " + severity + " diagnosis")),
                        Map.of("type", "section", "text", plain(details)),
                        Map.of("type", "section", "text", plain(summary))));
    }

    static String safe(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "Unavailable";
        }
        String clean = CREDENTIAL.matcher(CONTROLS.matcher(value).replaceAll(" ")).replaceAll("[redacted]");
        clean = LINK.matcher(clean).replaceAll("[link omitted]").replace("@", "[at]")
                .replace("<", "(").replace(">", ")").strip();
        if (clean.codePointCount(0,clean.length()) <= limit) {
            return clean;
        }
        return clean.substring(0,clean.offsetByCodePoints(0,limit-1)) + "…";
    }

    private static Map<String,String> plain(String text) {
        return Map.of("type", "plain_text", "text", text);
    }
}
