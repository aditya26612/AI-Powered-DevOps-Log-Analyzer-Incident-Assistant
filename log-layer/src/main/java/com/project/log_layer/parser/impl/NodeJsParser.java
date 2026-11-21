package com.project.log_layer.parser.impl;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.AbstractLogParser;
import com.project.log_layer.parser.model.ParsedLogData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resilient parser for Node.js / JavaScript applications (Express, NestJS, Winston, Pino).
 * Captures ISO timestamps, bracketed severity tags, and V8 exception stack traces.
 */
@Component
public class NodeJsParser extends AbstractLogParser {

    private static final Pattern NODE_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[+-]\\d{2}:?\\d{2}|Z)?)\\s+" +
                    "(?:\\[?(" + LEVEL + ")\\]?\\s*:?\\s+)?" +
                    "(?:(\\S+)\\s*:\\s*)?" +
                    "(.*)$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    @Override
    public LogSource getSupportedSource() {
        return LogSource.NODEJS;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {
        validate(request);
        String rawLog = request.getRawLog();

        Matcher matcher = NODE_LOG_PATTERN.matcher(rawLog);

        if (matcher.find()) {
            LocalDateTime timestamp = parseFlexibleTimestamp(matcher.group(1));
            String levelStr = matcher.group(2);
            String serviceOrModule = matcher.group(3);
            String message = matcher.group(4);

            LogLevel level = (levelStr != null && !levelStr.isBlank())
                    ? parseFlexibleLogLevel(levelStr)
                    : detectSeverityFromText(rawLog);

            return ParsedLogData.builder()
                    .timestamp(timestamp)
                    .logLevel(level)
                    .threadName("event-loop")
                    .loggerName(serviceOrModule != null ? serviceOrModule : request.getApplicationName())
                    .message(message != null ? message.trim() : rawLog.trim())
                    .applicationName(safeTrim(request.getApplicationName()))
                    .serviceName(safeTrim(request.getApplicationName()))
                    .hostName(safeTrim(request.getHostName()))
                    .environment(request.getEnvironment() != null ? request.getEnvironment() : com.project.log_layer.enums.Environment.PRODUCTION)
                    .source(request.getSource())
                    .rawLog(rawLog)
                    .build();
        }

        return fallbackGenericParse(rawLog, request, LogSource.NODEJS);
    }
}
