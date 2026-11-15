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
 * Resilient parser for Go applications (Zap, Logrus, Slog, standard log).
 * Captures Go timestamps, caller functions, and goroutine panic stack traces.
 */
@Component
public class GoParser extends AbstractLogParser {

    private static final Pattern GO_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s+" +
                    "(?:\\[?(\\w+)\\]?\\s+)?" +
                    "(.*)$",
            Pattern.DOTALL
    );

    @Override
    public LogSource getSupportedSource() {
        return LogSource.GO;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {
        validate(request);
        String rawLog = request.getRawLog();

        try {
            Matcher matcher = GO_LOG_PATTERN.matcher(rawLog);

            if (matcher.find()) {
                LocalDateTime timestamp = parseFlexibleTimestamp(matcher.group(1));
                String levelStr = matcher.group(2);
                String message = matcher.group(3);

                LogLevel level = (levelStr != null && !levelStr.isBlank())
                        ? parseFlexibleLogLevel(levelStr)
                        : detectSeverityFromText(rawLog);

                return ParsedLogData.builder()
                        .timestamp(timestamp)
                        .logLevel(level)
                        .threadName("goroutine")
                        .loggerName(request.getApplicationName())
                        .message(message != null ? message.trim() : rawLog.trim())
                        .applicationName(safeTrim(request.getApplicationName()))
                        .serviceName(safeTrim(request.getApplicationName()))
                        .hostName(safeTrim(request.getHostName()))
                        .environment(request.getEnvironment() != null ? request.getEnvironment() : com.project.log_layer.enums.Environment.PRODUCTION)
                        .source(request.getSource())
                        .rawLog(rawLog)
                        .build();
            }
        } catch (Exception ignored) {
            // Fall through to resilient fallback
        }

        return fallbackGenericParse(rawLog, request, LogSource.GO);
    }
}
