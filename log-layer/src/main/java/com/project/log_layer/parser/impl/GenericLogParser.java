package com.project.log_layer.parser.impl;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.AbstractLogParser;
import com.project.log_layer.parser.model.ParsedLogData;
import org.springframework.stereotype.Component;

/**
 * Universal fallback parser for custom, system, and generic application logs.
 *
 * Provides resilient best-effort field extraction and ensures that unexpected
 * formats or stack traces are never rejected with 400 errors.
 */
@Component
public class GenericLogParser extends AbstractLogParser {

    @Override
    public LogSource getSupportedSource() {
        return LogSource.CUSTOM;
    }

    @Override
    public ParsedLogData parse(LogIngestRequest request) {
        validate(request);
        LogSource source = request.getSource() != null ? request.getSource() : LogSource.CUSTOM;
        return fallbackGenericParse(request.getRawLog(), request, source);
    }

}

