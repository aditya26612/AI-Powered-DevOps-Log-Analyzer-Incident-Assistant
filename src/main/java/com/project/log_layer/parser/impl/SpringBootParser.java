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
 * Parser implementation for Spring Boot Logback logs.
 *
 * <p>
 * Supported format:
 *
 * 2026-06-26T20:15:31.123+05:30 INFO 12345 --- [main]
 * com.project.service.UserService : User created successfully
 *
 * Converts a raw Spring Boot log into a structured
 * {@link ParsedLogData}.
 */
@Component
public class SpringBootParser extends AbstractLogParser {

    @Override
    public LogSource getSupportedSource() {
        return LogSource.SPRING_BOOT;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {

        // Validate request
        validate(request);

        // Match Spring Boot log
        Matcher matcher = requireMatch(
                RegexPatterns.SPRING_BOOT_PATTERN,
                request.getRawLog()
        );

        // Extract fields
        String timestamp = extractGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String level = extractGroup(
                matcher,
                RegexGroupNames.LEVEL
        );

        String message = extractGroup(
                matcher,
                RegexGroupNames.MESSAGE
        );

        String thread = extractOptionalGroup(
                matcher,
                RegexGroupNames.THREAD
        );

        String logger = extractOptionalGroup(
                matcher,
                RegexGroupNames.LOGGER
        );

        String pid = extractOptionalGroup(
                matcher,
                RegexGroupNames.PID
        );

        String applicationName = safeTrim(
                request.getApplicationName()
        );

        Map<String, String> metadata = buildMetadata();

        populateMetadata(
                metadata,
                pid,
                logger
        );

        return ParsedLogData.builder()
                .timestamp(
                        parseTimestamp(
                                timestamp,
                                DateTimeFormats.SPRING_BOOT
                        )
                )
                .logLevel(
                        parseLogLevel(level)
                )
                .message(
                        normalizeWhitespace(message)
                )
                .loggerName(
                        safeTrim(logger)
                )
                .threadName(
                        safeTrim(thread)
                )
                .applicationName(
                        applicationName
                )
                .serviceName(
                        applicationName
                )
                .hostName(
                        safeTrim(request.getHostName())
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
     * Populates parser-specific metadata.
     *
     * <p>
     * Spring Boot metadata:
     * <ul>
     *     <li>Process ID (PID)</li>
     *     <li>Package Name</li>
     *     <li>Class Name</li>
     * </ul>
     *
     * @param metadata metadata map
     * @param pid process id
     * @param logger fully qualified logger name
     */
    private void populateMetadata(
            Map<String, String> metadata,
            String pid,
            String logger) {

        if (pid != null && !pid.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.PID,
                    pid
            );
        }

        if (logger == null || logger.isBlank()) {
            return;
        }

        int lastDot = logger.lastIndexOf('.');

        if (lastDot > 0) {

            metadata.put(
                    ParserMetadataKeys.PACKAGE_NAME,
                    logger.substring(0, lastDot)
            );

            metadata.put(
                    ParserMetadataKeys.CLASS_NAME,
                    logger.substring(lastDot + 1)
            );

        } else {

            metadata.put(
                    ParserMetadataKeys.CLASS_NAME,
                    logger
            );

        }
    }

    /**
     * Adds a metadata entry only when the value is present.
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
            metadata.put(key, value);
        }
    }
}