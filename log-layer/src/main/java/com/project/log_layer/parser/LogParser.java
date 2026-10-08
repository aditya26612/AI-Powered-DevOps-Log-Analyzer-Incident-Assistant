package com.project.log_layer.parser;

import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.parser.model.ParsedLogData;

/**
 * Contract for all log parsers.
 *
 * <p>Each parser implementation is responsible for converting
 * a raw log from a specific source into a structured
 * {@link ParsedLogData} object.</p>
 *
 * <p>Implementations should only perform parsing and must not
 * contain persistence or business logic.</p>
 */
public interface LogParser {

    /**
     * Returns the log source supported by this parser.
     *
     * @return supported log source
     */
    LogSource getSupportedSource();

    /**
     * Parses a raw log into structured data.
     *
     * @param request log ingestion request
     * @return structured parsed log
     */
    ParsedLogData parse(LogIngestRequest request);

}