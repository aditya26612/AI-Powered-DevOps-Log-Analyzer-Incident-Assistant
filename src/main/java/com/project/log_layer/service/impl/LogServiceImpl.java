package com.project.log_layer.service.impl;

import com.project.log_layer.dto.request.analysis.LogAnalysisRequest;
import com.project.log_layer.dto.request.ingest.BatchLogRequest;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.request.search.LogFilterRequest;
import com.project.log_layer.dto.response.analysis.LogAnalysisResponse;
import com.project.log_layer.dto.response.common.BatchOperationResponse;
import com.project.log_layer.dto.response.common.PagedResponse;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.entity.Log;
import com.project.log_layer.mapper.LogMapper;
import com.project.log_layer.parser.LogParser;
import com.project.log_layer.parser.ParserFactory;
import com.project.log_layer.parser.mapper.ParsedLogMapper;
import com.project.log_layer.parser.model.ParsedLogData;
import com.project.log_layer.repository.LogRepository;
import com.project.log_layer.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LogServiceImpl implements LogService {

    private final LogRepository logRepository;

    private final ParserFactory parserFactory;

    private final ParsedLogMapper parsedLogMapper;

    private final LogMapper logMapper;

    /**
     * Ingests a single log.
     */
    @Override
    public LogResponse ingest(LogIngestRequest request) {

        ParsedLogData parsedLogData = parseLog(request);

        Log log = parsedLogMapper.toEntity(parsedLogData);

        enrichLogEntity(log, request);

        Log savedLog = logRepository.save(log);

        return logMapper.toLogResponse(savedLog);
    }

    /**
     * Parses the incoming log using the appropriate parser.
     */
    private ParsedLogData parseLog(LogIngestRequest request) {

        LogParser parser = parserFactory.getParser(
                request.getSource()
        );

        return parser.parse(request);
    }

    /**
     * Populates fields managed by the system.
     */
    private void enrichLogEntity(
            Log log,
            LogIngestRequest request) {

        if (request.getCorrelationId() != null) {
            log.setCorrelationId(request.getCorrelationId());
        } else {
            log.setCorrelationId(UUID.randomUUID());
        }

    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public BatchOperationResponse ingestBatch(
            BatchLogRequest request) {

        throw new UnsupportedOperationException(
                "Batch ingestion is not implemented yet."
        );
    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public LogResponse getById(Long id) {

        throw new UnsupportedOperationException(
                "Get by id is not implemented yet."
        );
    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public PagedResponse<LogResponse> search(
            LogFilterRequest request) {

        throw new UnsupportedOperationException(
                "Search is not implemented yet."
        );
    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public void delete(Long id) {

        throw new UnsupportedOperationException(
                "Delete is not implemented yet."
        );
    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public boolean exists(Long id) {

        throw new UnsupportedOperationException(
                "Exists is not implemented yet."
        );
    }

    /**
     * Not implemented in Version 1.
     */
    @Override
    public LogAnalysisResponse analyze(
            Long id,
            LogAnalysisRequest request) {

        throw new UnsupportedOperationException(
                "Log analysis is not implemented yet."
        );
    }

}