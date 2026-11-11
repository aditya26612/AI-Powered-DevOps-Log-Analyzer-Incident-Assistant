package com.project.log_layer.parser.impl;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.AbstractLogParser;
import com.project.log_layer.parser.constant.RegexGroupNames;
import com.project.log_layer.parser.model.ParsedLogData;
import com.project.log_layer.util.DateTimeFormats;
import com.project.log_layer.util.ParserMetadataKeys;
import com.project.log_layer.util.RegexPatterns;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Parser implementation for Docker logs.
 *
 * <p>
 * Supported format:
 *
 * 2026-06-26T20:15:31.123456789Z
 * Started User Service
 *
 * Docker logs contain only:
 *
 * - Timestamp
 * - Message
 *
 * Everything else is supplied by the ingestion request.
 */
@Component
public class DockerParser extends AbstractLogParser {

    @Override
    public LogSource getSupportedSource() {
        return LogSource.DOCKER;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {

        validate(request);

        String rawLog = request.getRawLog().trim();
        String[] lines = rawLog.split("\\r?\\n");
        String firstLine = lines[0];

        Matcher matcher = RegexPatterns.DOCKER_PATTERN.matcher(firstLine);
        boolean matched = matcher.find();

        if (!matched) {
            return fallbackGenericParse(rawLog, request, LogSource.DOCKER);
        }

        String timestamp = extractOptionalGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String message = extractOptionalGroup(
                matcher,
                RegexGroupNames.MESSAGE
        );

        // Assemble multi-line content if container output contains a stack trace
        String completeMessage;
        if (lines.length > 1) {
            String remaining = java.util.Arrays.stream(lines, 1, lines.length)
                    .collect(java.util.stream.Collectors.joining("\n"));
            completeMessage = (message != null ? message : "") + "\n" + remaining;
        } else {
            completeMessage = message != null ? normalizeWhitespace(message) : "";
        }

        String applicationName = safeTrim(request.getApplicationName());
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = "docker-container";
        }

        // Intelligently detect severity from container output instead of hardcoding INFO
        LogLevel derivedLevel = detectSeverityFromText(completeMessage);

        Map<String, String> metadata = buildMetadata();

        return ParsedLogData.builder()

                .timestamp(
                        parseTimestamp(
                                timestamp,
                                DateTimeFormats.DOCKER
                        )
                )

                .logLevel(
                        derivedLevel
                )

                .message(
                        completeMessage
                )

                .loggerName(null)

                .threadName(null)

                .applicationName(
                        applicationName
                )

                .serviceName(
                        applicationName
                )

                .hostName(
                        safeTrim(
                                request.getHostName()
                        )
                )

                .environment(
                        request.getEnvironment()
                )

                .source(
                        request.getSource()
                )

                .rawLog(
                        request.getRawLog()
                )

                .metadata(
                        metadata
                )

                .build();

    }

    /**
     * Adds metadata only when a value exists.
     *
     * Reserved for future Docker metadata such as
     * container id and container name.
     */
    private void putIfPresent(
            Map<String, String> metadata,
            String key,
            String value) {

        if (value != null &&
                !value.isBlank()) {

            metadata.put(
                    key,
                    value
            );

        }

    }

}