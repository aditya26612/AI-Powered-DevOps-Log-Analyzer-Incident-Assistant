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

        Matcher matcher = requireMatch(
                RegexPatterns.DOCKER_PATTERN,
                request.getRawLog()
        );

        String timestamp = extractGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String message = extractGroup(
                matcher,
                RegexGroupNames.MESSAGE
        );

        String applicationName =
                safeTrim(request.getApplicationName());

        Map<String, String> metadata = buildMetadata();

        /*
         * Future enhancement:
         *
         * If Docker logs later include container id/name,
         * populate them here.
         */

        return ParsedLogData.builder()

                .timestamp(
                        parseTimestamp(
                                timestamp,
                                DateTimeFormats.DOCKER
                        )
                )

                .logLevel(
                        defaultLogLevel()
                )

                .message(
                        normalizeWhitespace(message)
                )

                /*
                 * Docker logs do not contain these fields.
                 */
                .loggerName(null)

                .threadName(null)

                .applicationName(
                        applicationName
                )

                /*
                 * Current default.
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