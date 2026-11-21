package com.project.log_layer.dto.request.ingest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;

import java.util.Map;
import java.util.UUID;

public record ExternalLogEvent(UUID eventId, String timestamp, LogLevel level, LogSource source,
                               String message, String exception, String loggerName, String threadName,
                               String hostName, UUID correlationId, Map<String, String> metadata,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String traceparent,
                               @JsonInclude(JsonInclude.Include.NON_NULL) TraceContext.Origin traceOrigin) {
    public ExternalLogEvent(UUID eventId, String timestamp, LogLevel level, LogSource source, String message,
                            String exception, String loggerName, String threadName, String hostName,
                            UUID correlationId, Map<String, String> metadata) {
        this(eventId,timestamp,level,source,message,exception,loggerName,threadName,hostName,correlationId,metadata,null,null);
    }
}
