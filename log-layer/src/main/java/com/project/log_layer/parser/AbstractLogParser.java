package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.exception.InvalidLogFormatException;
import com.project.log_layer.parser.model.ParsedLogData;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all log parsers.
 *
 * <p>
 * Provides common parsing utilities shared across every parser
 * implementation (Spring Boot, Docker, Nginx, Kubernetes, etc.).
 *
 * Concrete parsers should only focus on:
 * <ul>
 *     <li>Selecting the correct regex</li>
 *     <li>Extracting parser-specific fields</li>
 *     <li>Building {@code ParsedLogData}</li>
 * </ul>
 *
 * All common parsing logic lives here.
 */
public abstract class AbstractLogParser implements LogParser {

    /**
     * Validates the incoming ingestion request.
     *
     * @param request log ingestion request
     */
    protected final void validate(LogIngestRequest request) {

        if (request == null) {
            throw new InvalidLogFormatException(
                    "Log request cannot be null."
            );
        }

        if (request.getRawLog() == null ||
                request.getRawLog().isBlank()) {

            throw new InvalidLogFormatException(
                    "Raw log cannot be empty."
            );
        }
    }

    /**
     * Ensures the supplied pattern matches the log.
     *
     * Returns the matcher positioned on the first match.
     *
     * @param pattern compiled regex pattern
     * @param text raw log
     * @return matcher
     */
    protected final Matcher requireMatch(
            Pattern pattern,
            String text) {

        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {

            throw new InvalidLogFormatException(
                    "Invalid log format."
            );

        }

        return matcher;

    }

    /**
     * Extracts a required named regex group.
     *
     * @param matcher regex matcher
     * @param groupName named capturing group
     * @return extracted value
     */
    protected final String extractGroup(
            Matcher matcher,
            String groupName) {

        String value = matcher.group(groupName);

        if (value == null || value.isBlank()) {

            throw new InvalidLogFormatException(
                    "Missing required field: " + groupName
            );

        }

        return value;

    }

    /**
     * Extracts an optional named regex group.
     *
     * Returns null if the group does not exist or is empty.
     *
     * @param matcher regex matcher
     * @param groupName named capturing group
     * @return extracted value or null
     */
    protected final String extractOptionalGroup(
            Matcher matcher,
            String groupName) {

        try {

            String value = matcher.group(groupName);

            if (value == null || value.isBlank()) {
                return null;
            }

            return value;

        } catch (IllegalArgumentException ex) {

            return null;

        }

    }

    /**
     * Converts a timestamp into LocalDateTime with fallback resiliency.
     *
     * Supports ISO timestamps with timezone offsets, local date-time,
     * common date-time strings, and defaults to now() if parsing fails.
     *
     * @param timestamp timestamp text
     * @param formatter formatter
     * @return LocalDateTime
     */
    protected LocalDateTime parseTimestamp(
            String timestamp,
            DateTimeFormatter formatter) {

        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime
                    .parse(timestamp, formatter)
                    .toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return parseFlexibleTimestamp(timestamp);
        }
    }

    /**
     * Resilient timestamp parser trying multiple enterprise formats
     * before falling back to LocalDateTime.now().
     */
    protected LocalDateTime parseFlexibleTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }

        String cleaned = timestamp.trim();

        // Try standard ISO Offset (e.g. 2026-06-26T20:15:31.123+05:30 or Z)
        try {
            return OffsetDateTime.parse(cleaned, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {}

        // Try ISO Local (e.g. 2026-06-26T20:15:31.123)
        try {
            return LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}

        // Try space-separated (e.g. 2026-06-26 20:15:31.123 or 2026-06-26 20:15:31)
        try {
            String isoCandidate = cleaned.replace(" ", "T");
            return LocalDateTime.parse(isoCandidate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}

        return LocalDateTime.now();
    }

    /**
     * Converts log level text into LogLevel enum with flexible alias mapping.
     *
     * @param level log level text
     * @return LogLevel
     */
    protected LogLevel parseLogLevel(String level) {
        return parseFlexibleLogLevel(level);
    }

    /**
     * Resilient log level mapper that handles variations (FATAL, WARNING, ERR, etc.)
     * without throwing exceptions.
     */
    protected LogLevel parseFlexibleLogLevel(String level) {
        if (level == null || level.isBlank()) {
            return defaultLogLevel();
        }

        String normalized = level.trim().toUpperCase();

        return switch (normalized) {
            case "FATAL", "CRITICAL", "SEVERE", "ERROR", "ERR" -> LogLevel.ERROR;
            case "WARN", "WARNING" -> LogLevel.WARN;
            case "DEBUG" -> LogLevel.DEBUG;
            case "TRACE" -> LogLevel.TRACE;
            default -> LogLevel.INFO;
        };
    }

    /**
     * Detects severity from message text using heuristic keywords.
     */
    protected LogLevel detectSeverityFromText(String text) {
        if (text == null || text.isBlank()) {
            return defaultLogLevel();
        }

        String upper = text.toUpperCase();
        if (upper.contains("FATAL") || upper.contains("CRITICAL") || upper.contains("ERROR")
                || upper.contains("EXCEPTION") || upper.contains("FAIL") || upper.contains("PANIC")
                || upper.contains("CRASHLOOPBACKOFF") || upper.contains("OOMKILLED")) {
            return LogLevel.ERROR;
        }

        if (upper.contains("WARN") || upper.contains("WARNING") || upper.contains("DEPRECATED")) {
            return LogLevel.WARN;
        }

        return defaultLogLevel();
    }

    /**
     * Industrial Fallback Parser: Guarantees that unformatted or unexpected
     * log payloads are parsed safely instead of throwing 400 INVALID_LOG_FORMAT.
     */
    protected ParsedLogData fallbackGenericParse(
            String rawLog,
            LogIngestRequest request,
            com.project.log_layer.enums.LogSource source) {

        String trimmed = rawLog != null ? rawLog.trim() : "";
        LogLevel level = detectSeverityFromText(trimmed);
        LocalDateTime timestamp = parseFlexibleTimestamp(null);

        // Attempt to extract leading timestamp if present
        String[] tokens = trimmed.split("\\s+", 3);
        if (tokens.length > 0 && tokens[0].length() >= 10 && tokens[0].contains("-")) {
            timestamp = parseFlexibleTimestamp(tokens[0]);
        }

        String appName = safeTrim(request.getApplicationName());
        if (appName == null || appName.isBlank()) {
            appName = source.name().toLowerCase() + "-service";
        }

        return ParsedLogData.builder()
                .timestamp(timestamp)
                .logLevel(level)
                .message(trimmed)
                .applicationName(appName)
                .serviceName(appName)
                .hostName(safeTrim(request.getHostName()))
                .environment(request.getEnvironment())
                .source(source)
                .rawLog(rawLog)
                .metadata(buildMetadata())
                .build();
    }

    /**
     * Safely trims a string.
     *
     * @param value input string
     * @return trimmed value or null
     */
    protected final  String safeTrim(String value) {

        return value == null
                ? null
                : value.trim();

    }

    /**
     * Replaces multiple whitespaces with a single space.
     *
     * @param value input string
     * @return normalized string
     */
    protected final String normalizeWhitespace(String value) {

        if (value == null) {
            return null;
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");

    }

    /**
     * Creates a metadata map used by parser implementations.
     *
     * @return empty metadata map
     */
    protected final Map<String, String> buildMetadata() {

        return new HashMap<>();

    }

    /**
     * Derives a log level from an HTTP status code.
     *
     * <p>
     * Useful for web server logs (Nginx, Apache).
     * </p>
     *
     * @param statusCode HTTP status code
     * @return derived LogLevel
     */
    protected LogLevel parseHttpStatusLevel(String statusCode) {

        try {

            int status = Integer.parseInt(statusCode);

            if (status >= 500) {
                return LogLevel.ERROR;
            }

            if (status >= 400) {
                return LogLevel.WARN;
            }

            return LogLevel.INFO;

        } catch (NumberFormatException ex) {

            return LogLevel.INFO;

        }

    }

    /**
     * Returns the default log level when the source
     * does not provide an explicit severity.
     */
    protected LogLevel defaultLogLevel() {
        return LogLevel.INFO;
    }

}