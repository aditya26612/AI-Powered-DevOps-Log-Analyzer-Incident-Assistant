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
 * Parser implementation for Nginx access logs.
 *
 * <p>
 * Supported format:
 *
 * 192.168.1.10 - - [26/Jun/2026:20:15:31 +0530]
 * "GET /login HTTP/1.1"
 * 200
 * 512
 * "-"
 * "Mozilla/5.0"
 *
 * Converts a raw Nginx access log into ParsedLogData.
 */
@Component
public class NginxParser extends AbstractLogParser {

    @Override
    public LogSource getSupportedSource() {
        return LogSource.NGINX;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {

        validate(request);

        Matcher matcher = requireMatch(
                RegexPatterns.NGINX_PATTERN,
                request.getRawLog()
        );

        String timestamp = extractGroup(
                matcher,
                RegexGroupNames.TIMESTAMP
        );

        String clientIp = extractGroup(
                matcher,
                RegexGroupNames.CLIENT_IP
        );

        String method = extractGroup(
                matcher,
                RegexGroupNames.METHOD
        );

        String url = extractGroup(
                matcher,
                RegexGroupNames.URL
        );

        String protocol = extractGroup(
                matcher,
                RegexGroupNames.PROTOCOL
        );

        String status = extractGroup(
                matcher,
                RegexGroupNames.STATUS
        );

        String bytes = extractGroup(
                matcher,
                RegexGroupNames.BYTES
        );

        String referrer = extractOptionalGroup(
                matcher,
                RegexGroupNames.REFERRER
        );

        String userAgent = extractOptionalGroup(
                matcher,
                RegexGroupNames.USER_AGENT
        );

        String applicationName = safeTrim(
                request.getApplicationName()
        );

        Map<String, String> metadata = buildMetadata();

        populateMetadata(
                metadata,
                clientIp,
                method,
                url,
                protocol,
                status,
                bytes,
                referrer,
                userAgent
        );

        return ParsedLogData.builder()

                .timestamp(
                        parseTimestamp(
                                timestamp,
                                DateTimeFormats.NGINX
                        )
                )

                .logLevel(
                        parseHttpStatusLevel(status)
                )

                .message(
                        normalizeWhitespace(
                                method + " " + url
                        )
                )

                // Nginx access logs do not contain logger/thread
                .loggerName(null)

                .threadName(null)

                .applicationName(
                        applicationName
                )

                // Default service name
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
     * Populates Nginx-specific metadata.
     */
    private void populateMetadata(
            Map<String, String> metadata,
            String clientIp,
            String method,
            String url,
            String protocol,
            String status,
            String bytes,
            String referrer,
            String userAgent) {

        if (clientIp != null && !clientIp.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.CLIENT_IP,
                    clientIp
            );
        }

        if (method != null && !method.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.HTTP_METHOD,
                    method
            );
        }

        if (url != null && !url.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.REQUEST_URL,
                    url
            );
        }

        if (protocol != null && !protocol.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.PROTOCOL,
                    protocol
            );
        }

        if (status != null && !status.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.STATUS_CODE,
                    status
            );
        }

        if (bytes != null && !bytes.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.RESPONSE_SIZE,
                    bytes
            );
        }

        if (referrer != null && !referrer.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.REFERRER,
                    referrer
            );
        }

        if (userAgent != null && !userAgent.isBlank()) {
            metadata.put(
                    ParserMetadataKeys.USER_AGENT,
                    userAgent
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