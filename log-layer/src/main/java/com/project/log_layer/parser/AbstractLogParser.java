package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.exception.InvalidLogFormatException;
import com.project.log_layer.parser.model.ParsedLogData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
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

    private static final int MAX_RAW_LENGTH = 16_384;
    protected static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}"
            + "(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?";
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile("^" + TIMESTAMP + "(?=\\s|$)");
    protected static final String LEVEL = "TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|ERR|FATAL|CRITICAL|SEVERE";
    private static final Pattern EXPLICIT_LEVEL_PREFIX = Pattern.compile(
            "^(?:(?:" + TIMESTAMP + ")\\s+)?(?:\\[(?<bracket>" + LEVEL
                    + ")\\]|(?<level>" + LEVEL + "))(?=\\s|:|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYTHON_TRACEBACK = Pattern.compile(
            "^(?:" + TIMESTAMP + "\\s+)?Traceback \\(most recent call last\\):\\R[ \\t]+File ");
    private static final ObjectMapper STRUCTURED_LOG = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12)
                    .maxStringLength(MAX_RAW_LENGTH).maxNumberLength(128).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
        String raw = request.getRawLog();
        if (raw.length() > MAX_RAW_LENGTH || raw.indexOf(0) >= 0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(raw)) {
            throw new InvalidLogFormatException("Raw log exceeds the supported text limits.");
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
            return LocalDateTime.now(ZoneOffset.UTC);
        }

        try {
            return OffsetDateTime
                    .parse(timestamp, formatter)
                    .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
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
            return LocalDateTime.now(ZoneOffset.UTC);
        }

        String cleaned = timestamp.trim().replace(' ', 'T').replace(',', '.')
                .replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");

        // Try standard ISO Offset (e.g. 2026-06-26T20:15:31.123+05:30 or Z)
        try {
            return OffsetDateTime.parse(cleaned, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {}

        // Try ISO Local (e.g. 2026-06-26T20:15:31.123)
        try {
            return LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {}

        return LocalDateTime.now(ZoneOffset.UTC);
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

        String normalized = level.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "FATAL" -> LogLevel.FATAL;
            case "CRITICAL", "SEVERE", "ERROR", "ERR" -> LogLevel.ERROR;
            case "WARN", "WARNING" -> LogLevel.WARN;
            case "DEBUG" -> LogLevel.DEBUG;
            case "TRACE" -> LogLevel.TRACE;
            default -> LogLevel.INFO;
        };
    }

    /**
     * Recognizes a severity header or a Python traceback header followed by a frame.
     */
    protected LogLevel detectSeverityFromText(String text) {
        if (text == null || text.isBlank()) {
            return defaultLogLevel();
        }

        Matcher explicitLevel = EXPLICIT_LEVEL_PREFIX.matcher(text.trim());
        if (explicitLevel.find()) {
            String level = explicitLevel.group("level");
            return parseFlexibleLogLevel(level == null ? explicitLevel.group("bracket") : level);
        }

        if (PYTHON_TRACEBACK.matcher(text.trim()).find()) {
            return LogLevel.ERROR;
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
        JsonNode structured = structuredLog(trimmed);
        String message = textField(structured, "message", MAX_RAW_LENGTH);
        if (message != null && (message.indexOf(0) >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(message))) {
            message = null;
        }
        boolean recognized = message != null;
        Matcher timestamp = TIMESTAMP_PREFIX.matcher(trimmed);
        String appName = safeTrim(request.getApplicationName());
        if (appName == null || appName.isBlank()) {
            appName = source.name().toLowerCase(Locale.ROOT) + "-service";
        }

        return ParsedLogData.builder()
                .timestamp(parseFlexibleTimestamp(recognized ? textField(structured, "timestamp", 40)
                        : timestamp.find() ? timestamp.group() : null))
                .logLevel(recognized ? parseFlexibleLogLevel(textField(structured, "level", 20))
                        : detectSeverityFromText(trimmed))
                .message(recognized ? message : trimmed)
                .applicationName(appName)
                .serviceName(appName)
                .hostName(safeTrim(request.getHostName()))
                .environment(request.getEnvironment())
                .source(source)
                .rawLog(request.getRawLog())
                .fallback(!recognized)
                .metadata(buildMetadata())
                .build();
    }

    private static JsonNode structuredLog(String input) {
        if (!input.startsWith("{")) {
            return null;
        }
        try {
            return STRUCTURED_LOG.readTree(input);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String textField(JsonNode document, String name, int limit) {
        JsonNode field = document == null ? null : document.get(name);
        return field != null && field.isTextual() && !field.textValue().isBlank() && field.textValue().length() <= limit
                ? field.textValue() : null;
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
