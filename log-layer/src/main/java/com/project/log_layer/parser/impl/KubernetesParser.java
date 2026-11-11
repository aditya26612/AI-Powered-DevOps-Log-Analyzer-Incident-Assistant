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
 * Parser implementation for Kubernetes container logs.
 *
 * <p>
 * Supported format:
 *
 * 2026-06-26T20:15:31.123456789Z stdout F Started User Service
 *
 * Kubernetes logs contain:
 * <ul>
 *     <li>Timestamp</li>
 *     <li>Stream (stdout/stderr)</li>
 *     <li>Log flag</li>
 *     <li>Message</li>
 * </ul>
 *
 * Everything else is supplied by the ingestion request.
 */
@Component
public class KubernetesParser extends AbstractLogParser {

    @Override
    public LogSource getSupportedSource() {
        return LogSource.KUBERNETES;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {

        validate(request);

        String rawLog = request.getRawLog().trim();
        String[] lines = rawLog.split("\\r?\\n");
        String firstLine = lines[0];

        Matcher matcher = RegexPatterns.KUBERNETES_PATTERN.matcher(firstLine);
        boolean matched = matcher.find();

        if (!matched) {
            return fallbackGenericParse(rawLog, request, LogSource.KUBERNETES);
        }

        String timestamp = extractOptionalGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String stream = extractOptionalGroup(
                matcher,
                RegexGroupNames.STREAM
        );

        String flag = extractOptionalGroup(
                matcher,
                RegexGroupNames.FLAG
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

        String applicationName = safeTrim(
                request.getApplicationName()
        );
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = "k8s-pod";
        }

        // Detect log level: stderr is ERROR; otherwise detect from message content
        LogLevel derivedLevel;
        if ("stderr".equalsIgnoreCase(stream)) {
            derivedLevel = LogLevel.ERROR;
        } else {
            derivedLevel = detectSeverityFromText(completeMessage);
        }

        Map<String, String> metadata = buildMetadata();

        populateMetadata(
                metadata,
                stream,
                flag
        );

        return ParsedLogData.builder()

                .timestamp(
                        parseTimestamp(
                                timestamp,
                                DateTimeFormats.KUBERNETES
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
     * Populates Kubernetes-specific metadata.
     *
     * @param metadata metadata map
     * @param stream stdout/stderr
     * @param flag Kubernetes log flag
     */
    private void populateMetadata(
            Map<String, String> metadata,
            String stream,
            String flag) {

        putIfPresent(
                metadata,
                ParserMetadataKeys.STREAM,
                stream
        );

        putIfPresent(
                metadata,
                ParserMetadataKeys.FLAG,
                flag
        );

    }

    /**
     * Adds metadata only when a value is present.
     *
     * @param metadata metadata map
     * @param key metadata key
     * @param value metadata value
     */
    private void putIfPresent(
            Map<String, String> metadata,
            String key,
            String value) {

        if (value != null && !value.isBlank()) {
            metadata.put(
                    key,
                    value
            );
        }

    }

}