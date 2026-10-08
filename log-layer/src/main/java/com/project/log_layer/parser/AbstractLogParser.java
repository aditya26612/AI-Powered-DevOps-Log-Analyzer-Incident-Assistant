package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.exception.InvalidLogFormatException;

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
     * Converts a timestamp into LocalDateTime.
     *
     * Supports ISO timestamps with timezone offsets.
     *
     * @param timestamp timestamp text
     * @param formatter formatter
     * @return LocalDateTime
     */
    protected LocalDateTime parseTimestamp(
            String timestamp,
            DateTimeFormatter formatter) {

        try {

            return OffsetDateTime
                    .parse(timestamp, formatter)
                    .toLocalDateTime();

        } catch (DateTimeParseException ex) {

            throw new InvalidLogFormatException(
                    "Invalid timestamp: " + timestamp
            );

        }

    }

    /**
     * Converts log level text into LogLevel enum.
     *
     * @param level log level text
     * @return LogLevel
     */
    protected LogLevel parseLogLevel(String level) {

        try {

            return LogLevel.valueOf(
                    level.trim().toUpperCase()
            );

        } catch (IllegalArgumentException ex) {

            throw new InvalidLogFormatException(
                    "Unknown log level: " + level
            );

        }

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