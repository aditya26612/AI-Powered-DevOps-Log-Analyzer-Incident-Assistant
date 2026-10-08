package com.project.log_layer.parser.impl;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
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

        Matcher matcher = requireMatch(
                RegexPatterns.KUBERNETES_PATTERN,
                request.getRawLog()
        );

        String timestamp = extractGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String stream = extractGroup(
                matcher,
                RegexGroupNames.STREAM
        );

        String flag = extractGroup(
                matcher,
                RegexGroupNames.FLAG
        );

        String message = extractGroup(
                matcher,
                RegexGroupNames.MESSAGE
        );

        String applicationName = safeTrim(
                request.getApplicationName()
        );

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
                        defaultLogLevel()
                )

                .message(
                        normalizeWhitespace(message)
                )

                /*
                 * Kubernetes container logs do not contain
                 * logger or thread information.
                 */
                .loggerName(null)

                .threadName(null)

                .applicationName(
                        applicationName
                )

                /*
                 * Current default implementation.
                 */
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