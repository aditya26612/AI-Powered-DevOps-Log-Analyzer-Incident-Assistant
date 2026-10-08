package com.project.log_layer.service;

import com.project.log_layer.dto.request.analysis.LogAnalysisRequest;
import com.project.log_layer.dto.request.ingest.BatchLogRequest;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.request.search.LogFilterRequest;
import com.project.log_layer.dto.response.analysis.LogAnalysisResponse;
import com.project.log_layer.dto.response.common.BatchOperationResponse;
import com.project.log_layer.dto.response.common.PagedResponse;
import com.project.log_layer.dto.response.log.LogResponse;

/**
 * Business contract for log management.
 *
 * Coordinates parsing, persistence, searching,
 * and future AI analysis.
 */
public interface LogService {

    /**
     * Ingest a single log.
     */
    LogResponse ingest(LogIngestRequest request);

    /**
     * Ingest multiple logs.
     */
    BatchOperationResponse ingestBatch(BatchLogRequest request);

    /**
     * Find log by id.
     */
    LogResponse getById(Long id);

    /**
     * Search logs.
     */
    PagedResponse<LogResponse> search(LogFilterRequest request);

    /**
     * Delete a log.
     */
    void delete(Long id);

    /**
     * Checks whether a log exists.
     */
    boolean exists(Long id);

    /**
     * Future AI endpoint.
     */
    LogAnalysisResponse analyze(
            Long id,
            LogAnalysisRequest request
    );
}