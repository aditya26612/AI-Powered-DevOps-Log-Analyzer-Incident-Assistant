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
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.exception.InvalidSearchCriteriaException;
import com.project.log_layer.exception.LogNotFoundException;
import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.integration.exception.LlmServiceException;
import com.project.log_layer.integration.exception.MlServiceException;
import com.project.log_layer.mapper.LlmAnalysisMapper;
import com.project.log_layer.mapper.LogMapper;
import com.project.log_layer.mapper.MlPredictionMapper;
import com.project.log_layer.parser.LogParser;
import com.project.log_layer.parser.ParserFactory;
import com.project.log_layer.parser.mapper.ParsedLogMapper;
import com.project.log_layer.parser.model.ParsedLogData;
import com.project.log_layer.repository.LogRepository;
import com.project.log_layer.service.LlmAnalysisFailureService;
import com.project.log_layer.service.LogService;
import com.project.log_layer.specification.LogSpecificationBuilder;
import com.project.log_layer.util.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LogServiceImpl implements LogService {

    private final LogSpecificationBuilder logSpecificationBuilder;

    private final LogRepository logRepository;

    private final ParserFactory parserFactory;

    private final ParsedLogMapper parsedLogMapper;

    private final LogMapper logMapper;

    private final MlInferenceClient mlInferenceClient;

    private final MlPredictionMapper mlPredictionMapper;

    private final LlmInferenceClient llmInferenceClient;

    private final LlmAnalysisMapper llmAnalysisMapper;

    private final LlmAnalysisFailureService llmAnalysisFailureService;

    private final HttpServletRequest httpServletRequest;


    /**
     * Generates a correlation ID for the log.
     *
     * If the client provides a correlation ID,
     * it is reused. Otherwise, a new UUID is generated.
     */
    private UUID generateCorrelationId(
            LogIngestRequest request) {

        return request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID();
    }


    /**
     * Ingests a single log.
     */
    @Override
    public LogResponse ingest(LogIngestRequest request) {

        ParsedLogData parsedLogData =
                parseLog(request);

        Log log =
                parsedLogMapper.toEntity(parsedLogData);

        /*
         * Associate the log with the authenticated user.
         *
         * The email comes from the X-User-Email header
         * inserted by the API Gateway after validating
         * the JWT.
         */
        log.setUserEmail(
                CurrentUser.getEmail(httpServletRequest)
        );

        enrichLogEntity(log, request);

        performMlAnalysis(log);

        Log savedLog =
                logRepository.save(log);

        return logMapper.toLogResponse(savedLog);
    }


    /**
     * Parses the incoming log using the appropriate parser.
     */
    private ParsedLogData parseLog(
            LogIngestRequest request) {

        LogParser parser =
                parserFactory.getParser(
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

        log.setCorrelationId(
                generateCorrelationId(request)
        );
    }


    /**
     * Sends the log to the ML service for prediction.
     */
    private void performMlAnalysis(
            Log logEntity) {

        try {

            MlPredictionRequest request =
                    mlPredictionMapper.toRequest(
                            logEntity
                    );

            MlPredictionResponse response =
                    mlInferenceClient.predict(request);

            logEntity.setPrediction(
                    response.prediction()
            );

            logEntity.setPredictionLabel(
                    response.predictionLabel()
            );

            logEntity.setDecisionScore(
                    response.decisionScore()
            );

            logEntity.setModelVersion(
                    response.modelVersion()
            );

            logEntity.setAnalysisStatus(
                    AnalysisStatus.COMPLETED
            );

            logEntity.setAnalyzedAt(
                    LocalDateTime.now()
            );

        } catch (MlServiceException ex) {

            logEntity.setAnalysisStatus(
                    AnalysisStatus.FAILED
            );

            logEntity.setAnalyzedAt(
                    LocalDateTime.now()
            );

            log.error(
                    "ML prediction failed for correlationId={}",
                    logEntity.getCorrelationId(),
                    ex
            );
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
     * Retrieves a log by its identifier.
     *
     * Only the authenticated user's logs can be retrieved.
     *
     * @param id log identifier
     * @return Log entity
     * @throws LogNotFoundException if the log does not exist
     *                              or belongs to another user
     */
    private Log getLogOrThrow(Long id) {

        String currentUser =
                CurrentUser.getEmail(
                        httpServletRequest
                );

        return logRepository
                .findById(id)
                .filter(log ->
                        currentUser.equals(
                                log.getUserEmail()
                        )
                )
                .orElseThrow(() ->
                        new LogNotFoundException(
                                "Log not found with id : " + id
                        )
                );
    }


    /**
     * Retrieves a log by its identifier.
     */
    @Override
    @Transactional(readOnly = true)
    public LogResponse getById(Long id) {

        Log log =
                getLogOrThrow(id);

        return logMapper.toLogResponse(log);
    }


    /**
     * Searches logs using dynamic filtering,
     * pagination and sorting.
     *
     * The search is automatically restricted
     * to the authenticated user's logs.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<LogResponse> search(
            LogFilterRequest request) {

        validateSearchRequest(request);

        String currentUser =
                CurrentUser.getEmail(
                        httpServletRequest
                );

        Pageable pageable =
                createPageable(request);

        Specification<Log> specification =
                buildSpecification(request);

        /*
         * Ownership condition:
         *
         * user_email = currently authenticated user
         */
        Specification<Log> ownershipSpecification =
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(
                                root.get("userEmail"),
                                currentUser
                        );

        /*
         * Combine ownership filtering
         * with all existing search filters.
         */
        Specification<Log> finalSpecification =
                ownershipSpecification.and(
                        specification
                );

        Page<Log> page =
                logRepository.findAll(
                        finalSpecification,
                        pageable
                );

        return logMapper.toPagedResponse(page);
    }


    /**
     * Deletes a log by its identifier.
     *
     * Ownership is automatically verified
     * through getLogOrThrow().
     */
    @Override
    public void delete(Long id) {

        Log log =
                getLogOrThrow(id);

        logRepository.delete(log);
    }


    /**
     * Checks whether a log exists for
     * the currently authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long id) {

        String currentUser =
                CurrentUser.getEmail(
                        httpServletRequest
                );

        return logRepository
                .findById(id)
                .map(log ->
                        currentUser.equals(
                                log.getUserEmail()
                        )
                )
                .orElse(false);
    }


    /**
     * Performs LLM analysis on a log.
     *
     * Ownership is automatically verified
     * through getLogOrThrow().
     */
    @Override
    public LogAnalysisResponse analyze(
            Long id,
            LogAnalysisRequest request) {

        Log logEntity =
                getLogOrThrow(id);

        try {

            LlmAnalysisRequest llmRequest =
                    llmAnalysisMapper.toRequest(
                            logEntity
                    );

            LlmAnalysisResponse llmResponse =
                    llmInferenceClient.analyze(
                            llmRequest
                    );

            logEntity.setLlmAnalysisStatus(
                    AnalysisStatus.COMPLETED
            );

            logEntity.setLlmAnalyzedAt(
                    LocalDateTime.now()
            );

            Log savedLog =
                    logRepository.save(logEntity);

            return LogAnalysisResponse
                    .builder()
                    .log(
                            logMapper.toLogResponse(
                                    savedLog
                            )
                    )
                    .summary(
                            llmResponse.summary()
                    )
                    .probableRootCause(
                            llmResponse.rootCause()
                    )
                    .suggestedFix(
                            llmResponse.recommendation()
                    )
                    .build();

        } catch (LlmServiceException ex) {

            llmAnalysisFailureService.markAsFailed(
                    logEntity
            );

            log.error(
                    "LLM analysis failed for logId={}",
                    id,
                    ex
            );

            throw ex;
        }
    }


    /**
     * Validates the supplied search request.
     *
     * @param request search request
     * @throws InvalidSearchCriteriaException
     *         if the request is invalid
     */
    private void validateSearchRequest(
            LogFilterRequest request) {

        if (request == null) {

            throw new InvalidSearchCriteriaException(
                    "Search request cannot be null."
            );
        }

        if (request.getStartTime() != null &&
                request.getEndTime() != null &&
                request.getStartTime()
                        .isAfter(request.getEndTime())) {

            throw new InvalidSearchCriteriaException(
                    "Start time cannot be after end time."
            );
        }
    }


    /**
     * Creates a pageable object for searching.
     */
    private Pageable createPageable(
            LogFilterRequest request) {

        return PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(
                        request.getSortDirection(),
                        request.getSortBy().getField()
                )
        );
    }


    /**
     * Builds the search specification.
     */
    private Specification<Log> buildSpecification(
            LogFilterRequest request) {

        return logSpecificationBuilder.build(request);
    }
}