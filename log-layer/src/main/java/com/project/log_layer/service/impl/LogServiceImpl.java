package com.project.log_layer.service.impl;

import com.project.log_layer.dto.request.analysis.LogAnalysisRequest;
import com.project.log_layer.dto.request.analyzer.LogInspectRequest;
import com.project.log_layer.dto.request.ingest.ExternalLogEvent;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.request.search.LogFilterRequest;
import com.project.log_layer.dto.response.analysis.LogAnalysisResponse;
import com.project.log_layer.dto.response.analyzer.DetectionMetrics;
import com.project.log_layer.dto.response.analyzer.IncidentDiagnosis;
import com.project.log_layer.dto.response.analyzer.LogInspectionResponse;
import com.project.log_layer.dto.response.common.PagedResponse;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.exception.InvalidSearchCriteriaException;
import com.project.log_layer.exception.InvalidLogFormatException;
import com.project.log_layer.exception.LogNotFoundException;
import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;
import com.project.log_layer.integration.exception.LlmServiceException;
import com.project.log_layer.integration.exception.MlServiceException;
import com.project.log_layer.incident.IncidentCorrelation;
import com.project.log_layer.diagnosis.DiagnosisQueue;
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
import com.project.log_layer.service.OperationalTriage;
import io.micrometer.core.instrument.Metrics;
import com.project.log_layer.specification.LogSpecificationBuilder;
import com.project.log_layer.util.CurrentUser;
import com.project.log_layer.security.TenantIdentity;
import com.project.log_layer.tenant.ApplicationCredentials;
import com.project.log_layer.tenant.TenantSqlScope;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.project.log_layer.notification.config.NotificationProperties;

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
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LogServiceImpl implements LogService {

    private final LogSpecificationBuilder logSpecificationBuilder;

    private final LogRepository logRepository;
    private final ApplicationCredentials applicationCredentials;
    private final TenantSqlScope tenantScope;
    private final IncidentCorrelation incidentCorrelation;
    private final DiagnosisQueue diagnosisQueue;

    private final ParserFactory parserFactory;

    private final ParsedLogMapper parsedLogMapper;

    private final LogMapper logMapper;

    private final MlInferenceClient mlInferenceClient;

    private final MlPredictionMapper mlPredictionMapper;

    private final LlmInferenceClient llmInferenceClient;

    private final LlmAnalysisMapper llmAnalysisMapper;

    private final LlmAnalysisFailureService llmAnalysisFailureService;

    private final HttpServletRequest httpServletRequest;


    private final NotificationProperties notificationProperties;

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
        UUID organizationId = TenantIdentity.organization(httpServletRequest);
        ApplicationCredentials.Binding binding = null;
        if (httpServletRequest.getAttribute(TenantIdentity.INGESTION) instanceof ApplicationCredentials.Binding verified) {
            binding = verified;
        } else if (organizationId != null) {
            if (request.getApplicationId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A registered application ID is required");
            }
            binding = applicationCredentials.activeApplication(organizationId, request.getApplicationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        }
        if (binding != null) {
            if (request.getApplicationId() != null && request.getApplicationId() != binding.applicationId()
                    || request.getApplicationName() != null && !request.getApplicationName().equals(binding.applicationName())
                    || request.getEnvironment() != null && request.getEnvironment() != binding.environment()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ingestion application does not match the credential");
            }
            request.setApplicationName(binding.applicationName());
            request.setEnvironment(binding.environment());
        }

        Log log = parsedLogMapper.toEntity(parseLog(request));
        log.setUserEmail(CurrentUser.getEmail(httpServletRequest));
        return logMapper.toLogResponse(persist(log, request, binding));
    }

    @Override
    public long ingestExternal(ExternalLogEvent event, ApplicationCredentials.Binding owner, byte[] digest) {
        tenantScope.enter(owner.organizationId());
        return saveAnalyzed(buildAndAnalyzeExternal(event, owner, digest)).getId();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Log prepareExternal(ExternalLogEvent event, ApplicationCredentials.Binding owner, byte[] digest) {
        return buildAndAnalyzeExternal(event, owner, digest);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long storePreparedExternal(Log log) {
        tenantScope.enter(log.getOrganizationId());
        return saveAnalyzed(log).getId();
    }

    private Log buildAndAnalyzeExternal(ExternalLogEvent event, ApplicationCredentials.Binding owner, byte[] digest) {
        var metadata = new HashMap<>(event.metadata());
        if (event.exception() != null) {
            metadata.put(Log.EXTERNAL_EXCEPTION_KEY, event.exception());
        }
        Log log = Log.builder()
                .userEmail(CurrentUser.SERVICE_EMAIL)
                .timestamp(LocalDateTime.ofInstant(Instant.parse(event.timestamp()), ZoneOffset.UTC))
                .level(event.level()).source(event.source()).message(event.message())
                .serviceName(owner.applicationName()).loggerName(event.loggerName())
                .threadName(event.threadName()).hostName(event.hostName())
                .externalEventId(event.eventId()).externalEventDigest(digest)
                .externalMetadata(metadata.isEmpty() ? null : metadata)
                .traceparent(event.traceparent()).traceOrigin(event.traceOrigin())
                .correlationId(event.correlationId() != null ? event.correlationId() : UUID.randomUUID())
                .correlationOrigin(event.correlationId() != null ? Log.CorrelationOrigin.COLLECTOR_SUPPLIED
                        : Log.CorrelationOrigin.SERVER_GENERATED)
                .build();
        log.setRawLog(event.timestamp() + " " + event.level() + " " + log.analysisMessage());
        assignApplication(log, owner);
        Metrics.counter("devinsight.log.parse", "outcome", "canonical").increment();
        performMlAnalysis(log);
        return log;
    }

    private Log persist(Log log, LogIngestRequest request, ApplicationCredentials.Binding binding) {
        enrichLogEntity(log, request);
        if (binding != null) {
            tenantScope.enter(binding.organizationId());
            assignApplication(log, binding);
        }
        performMlAnalysis(log);
        return saveAnalyzed(log);
    }

    private static void assignApplication(Log log, ApplicationCredentials.Binding binding) {
        log.setOrganizationId(binding.organizationId());
        log.setApplicationId(binding.applicationId());
        log.setApplicationName(binding.applicationName());
        log.setEnvironment(binding.environment());
    }

    private Log saveAnalyzed(Log log) {
        boolean eligible = shouldTriggerAutoDiagnosis(log);
        Log saved = logRepository.save(log);
        if (saved.getOrganizationId() == null) {
            return saved;
        }
        var association = saved.getExternalEventId() == null
                ? new IncidentCorrelation.Association(null, eligible)
                : incidentCorrelation.associate(saved, eligible);
        if (association.ownsDiagnosis()) {
            saved.setLlmAnalysisStatus(AnalysisStatus.PENDING);
            diagnosisQueue.enqueue(saved, association.incidentId());
        }
        return saved;
    }

    /**
     * Parses the incoming log using the appropriate parser.
     */
    private ParsedLogData parseLog(
            LogIngestRequest request) {

        try {
            LogParser parser = parserFactory.getParser(request.getSource());
            ParsedLogData parsed = parser.parse(request);
            if (parsed.getLoggerName() != null && parsed.getLoggerName().length() > 255) {
                parsed.setLoggerName(null);
                parsed.setFallback(true);
            }
            if (parsed.getThreadName() != null && parsed.getThreadName().length() > 100) {
                parsed.setThreadName(null);
                parsed.setFallback(true);
            }
            Metrics.counter("devinsight.log.parse", "outcome",
                    parsed.isFallback() ? "fallback" : "parsed").increment();
            return parsed;
        } catch (InvalidLogFormatException ex) {
            Metrics.counter("devinsight.log.parse", "outcome", "invalid_text").increment();
            throw ex;
        } catch (RuntimeException ex) {
            Metrics.counter("devinsight.log.parse", "outcome", "internal_failure").increment();
            throw ex;
        }
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
        log.setCorrelationOrigin(request.getCorrelationId() != null ? Log.CorrelationOrigin.LEGACY_SUPPLIED
                : Log.CorrelationOrigin.SERVER_GENERATED);

        if (log.getEnvironment() == null) {
            log.setEnvironment(request.getEnvironment() != null
                    ? request.getEnvironment()
                    : com.project.log_layer.enums.Environment.PRODUCTION);
        }

        if (log.getApplicationName() == null || log.getApplicationName().isBlank()) {
            log.setApplicationName(request.getApplicationName() != null ? request.getApplicationName() : "unknown-app");
        }

        if (log.getSource() == null && request.getSource() != null) {
            log.setSource(request.getSource());
        }

        if (log.getRawLog() == null && request.getRawLog() != null) {
            log.setRawLog(request.getRawLog());
        }
    }


    /**
     * Sends the log to the ML service for prediction.
     */
    private void performMlAnalysis(
            Log logEntity) {

        long started = System.nanoTime();
        try {

            MlPredictionRequest request =
                    mlPredictionMapper.toRequest(
                            logEntity
                    );

            MlPredictionResponse response =
                    mlInferenceClient.predict(request);

            if (response == null) {
                throw new MlServiceException("Missing ML prediction");
            }
            response.requireConsistentResult();
            logEntity.setAnomaly(response.anomaly());
            logEntity.setAnomalyScore(response.decisionScore());

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

            logEntity.setAnomaly(false);
            logEntity.setAnomalyScore(null);
            logEntity.setPrediction(null);
            logEntity.setPredictionLabel(null);
            logEntity.setDecisionScore(null);
            logEntity.setModelVersion(null);
            logEntity.setAnalysisStatus(
                    AnalysisStatus.FAILED
            );

            logEntity.setAnalyzedAt(
                    LocalDateTime.now()
            );

            log.debug("ML prediction unavailable: {}", ex.getMessage());
        } finally {
            Metrics.timer("devinsight.log.inference", "outcome", logEntity.getAnalysisStatus().name())
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            Metrics.counter("devinsight.log.triage",
                    "statistical", String.valueOf(OperationalTriage.statisticalAnomaly(logEntity)),
                    "operational", Boolean.toString(OperationalTriage.ruleId(logEntity) != null),
                    "eligible", Boolean.toString(OperationalTriage.eligible(logEntity))).increment();
        }
    }

    private boolean shouldTriggerAutoDiagnosis(Log log) {
        if (notificationProperties == null || !notificationProperties.isAutoDiagnoseAnomalies()) {
            return false;
        }
        return OperationalTriage.eligible(log);
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
        return visibleLog(id).orElseThrow(() -> new LogNotFoundException("Log not found"));
    }

    private Optional<Log> visibleLog(Long id) {
        UUID organization = TenantIdentity.organization(httpServletRequest);
        if (organization != null) {
            tenantScope.enter(organization);
            return logRepository.findByIdAndOrganizationId(id, organization);
        }
        String email = CurrentUser.getEmail(httpServletRequest);
        return logRepository.findById(id)
                .filter(log -> log.getOrganizationId() == null && email.equals(log.getUserEmail()));
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

        UUID organization = TenantIdentity.organization(httpServletRequest);
        Pageable pageable = createPageable(request);
        Specification<Log> specification = buildSpecification(request);
        Specification<Log> ownershipSpecification;
        if (organization != null) {
            tenantScope.enter(organization);
            ownershipSpecification = (root, query, builder) -> builder.equal(root.get("organizationId"), organization);
        } else {
            String email = CurrentUser.getEmail(httpServletRequest);
            ownershipSpecification = (root, query, builder) -> builder.and(
                    builder.isNull(root.get("organizationId")), builder.equal(root.get("userEmail"), email));
        }
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
        return visibleLog(id).isPresent();
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

        String durableState = logEntity.getOrganizationId() == null ? null
                : diagnosisQueue.stateForLog(logEntity.getOrganizationId(), id);
        if ("SUCCEEDED".equals(durableState)) {
            return LogAnalysisResponse.builder().log(logMapper.toLogResponse(logEntity))
                    .summary(logEntity.getLlmSummary()).probableRootCause(logEntity.getLlmRootCause())
                    .suggestedFix(logEntity.getLlmRecommendation()).build();
        }
        if (durableState != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the durable diagnosis status or retry action");
        }

        try {

            LlmAnalysisRequest llmRequest =
                    llmAnalysisMapper.toRequest(
                            logEntity
                    );

            LlmAnalysisResponse llmResponse =
                    llmInferenceClient.analyze(
                            llmRequest
                    );

            recordDiagnosis(logEntity, llmResponse);

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

    private void recordDiagnosis(Log entity, LlmAnalysisResponse response) {
        entity.setLlmSummary(response.summary());
        entity.setLlmRootCause(response.rootCause());
        entity.setLlmRecommendation(response.recommendation());
        entity.setLlmSeverity(response.severity());
        entity.setLlmAnalyzedAt(LocalDateTime.now());
        entity.setLlmAnalysisStatus(AnalysisStatus.COMPLETED);
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

    /**
     * Inspects a log in real-time using a 3-tier execution model:
     * - Tier 0/1: Resilient Multi-line Parsing + Heuristic Rule Check
     * - Tier 2: ML Isolation Forest Anomaly Detection
     * - Tier 3: Selective RAG + LLM Root Cause Diagnosis
     */
    @Override
    public LogInspectionResponse inspect(LogInspectRequest request) {
        long startTime = System.currentTimeMillis();

        if (request == null || request.getLog() == null || request.getLog().isBlank()) {
            throw new IllegalArgumentException("Log payload must not be blank.");
        }

        LogSource source = request.getAppType() != null ? request.getAppType() : LogSource.SPRING_BOOT;
        Environment env = request.getEnvironment() != null ? request.getEnvironment() : Environment.PRODUCTION;
        String appName = (request.getApplicationName() != null && !request.getApplicationName().isBlank())
                ? request.getApplicationName().trim()
                : source.name().toLowerCase() + "-service";
        UUID correlationId = request.getCorrelationId() != null ? request.getCorrelationId() : UUID.randomUUID();
        String analysisMode = (request.getAnalysisMode() != null && !request.getAnalysisMode().isBlank())
                ? request.getAnalysisMode().trim().toUpperCase()
                : "AUTO";

        LogIngestRequest ingestRequest = LogIngestRequest.builder()
                .rawLog(request.getLog())
                .source(source)
                .environment(env)
                .applicationName(appName)
                .hostName(request.getHostName())
                .correlationId(correlationId)
                .build();

        // 1. Resilient Parse
        ParsedLogData parsedLogData = parseLog(ingestRequest);

        // 2. Convert to entity
        Log logEntity = parsedLogMapper.toEntity(parsedLogData);
        logEntity.setCorrelationId(correlationId);
        logEntity.setCorrelationOrigin(request.getCorrelationId() != null ? Log.CorrelationOrigin.LEGACY_SUPPLIED
                : Log.CorrelationOrigin.SERVER_GENERATED);

        String userEmail = "system@analyzer.local";
        try {
            String email = CurrentUser.getEmail(httpServletRequest);
            if (email != null && !email.isBlank()) {
                userEmail = email;
            }
        } catch (Exception ignored) {}
        logEntity.setUserEmail(userEmail);
        UUID organization = TenantIdentity.organization(httpServletRequest);
        if (organization != null) {
            tenantScope.enter(organization);
        }
        logEntity.setOrganizationId(organization);

        // 3. Perform ML Anomaly Analysis (Tier 2)
        performMlAnalysis(logEntity);

        // 4. Heuristic / Deterministic Rule Check (Tier 1)
        List<String> heuristicFlags = new ArrayList<>();
        String ruleId = OperationalTriage.ruleId(logEntity);
        if (ruleId != null) {
            heuristicFlags.add(ruleId);
        }
        boolean hasStackTrace = parsedLogData.getMessage() != null &&
                (parsedLogData.getMessage().contains("\tat ") ||
                 parsedLogData.getMessage().contains("Exception:") ||
                 parsedLogData.getMessage().contains("Traceback"));
        if (hasStackTrace) {
            heuristicFlags.add("STACKTRACE_PRESENT");
        }
        if (Boolean.TRUE.equals(logEntity.getAnomaly())) {
            heuristicFlags.add("ML_ANOMALY_DETECTED");
        }

        // 5. Tier 3: LLM Root Cause Diagnosis (Selective)
        boolean eligible = OperationalTriage.eligible(logEntity);
        boolean shouldRunLlm = "FORCE_DEEP".equals(analysisMode) || ("AUTO".equals(analysisMode) && eligible);
        IncidentDiagnosis diagnosis = null;

        if (shouldRunLlm && !"FAST".equals(analysisMode)) {
            try {
                LlmAnalysisRequest llmRequest = llmAnalysisMapper.toRequest(logEntity);
                LlmAnalysisResponse llmResponse = llmInferenceClient.analyze(llmRequest);

                recordDiagnosis(logEntity, llmResponse);

                List<String> remediationCommands = extractRemediationCommands(llmResponse.recommendation());

                diagnosis = IncidentDiagnosis.builder()
                        .summary(llmResponse.summary())
                        .probableRootCause(llmResponse.rootCause())
                        .severity(llmResponse.severity() != null ? llmResponse.severity() : logEntity.getLevel().name())
                        .suggestedFix(llmResponse.recommendation())
                        .remediationCommands(remediationCommands)
                        .relevantKnowledgeDocs(List.of())
                        .build();

            } catch (Exception ex) {
                log.warn("Inspector diagnosis failed");
                logEntity.setLlmAnalysisStatus(AnalysisStatus.FAILED);
            }
        }

        // Persist to log repository for audit & log explorer
        try {
            logRepository.save(logEntity);
        } catch (Exception ex) {
            log.warn("Unable to save inspected log");
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;

        DetectionMetrics detectionMetrics = DetectionMetrics.builder()
                .isMlAnomaly(OperationalTriage.statisticalAnomaly(logEntity))
                .mlPrediction(logEntity.getPrediction())
                .mlPredictionLabel(logEntity.getPredictionLabel())
                .decisionScore(logEntity.getDecisionScore())
                .modelVersion(logEntity.getModelVersion())
                .heuristicFlags(heuristicFlags)
                .build();

        return LogInspectionResponse.builder()
                .correlationId(correlationId)
                .appType(source)
                .applicationName(appName)
                .environment(env)
                .timestamp(parsedLogData.getTimestamp())
                .level(parsedLogData.getLogLevel())
                .message(parsedLogData.getMessage())
                .hasStackTrace(hasStackTrace)
                .stackTrace(hasStackTrace ? parsedLogData.getMessage() : null)
                .hasAnomaly(Boolean.TRUE.equals(OperationalTriage.statisticalAnomaly(logEntity)))
                .analysisStatus(logEntity.getAnalysisStatus())
                .operationalRuleId(ruleId)
                .diagnosisEligible(eligible)
                .llmAnalysisStatus(logEntity.getLlmAnalysisStatus())
                .detectionMetrics(detectionMetrics)
                .incidentDiagnosis(diagnosis)
                .executionTimeMs(executionTimeMs)
                .metadata(parsedLogData.getMetadata())
                .build();
    }

    private List<String> extractRemediationCommands(String recommendation) {
        if (recommendation == null || recommendation.isBlank()) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        String[] lines = recommendation.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("$ ") || trimmed.startsWith("docker ") ||
                trimmed.startsWith("kubectl ") || trimmed.startsWith("systemctl ") ||
                trimmed.startsWith("netstat ") || trimmed.startsWith("lsof ") ||
                trimmed.startsWith("kill ") || trimmed.startsWith("mvn ") ||
                trimmed.startsWith("java ")) {
                commands.add(trimmed.startsWith("$ ") ? trimmed.substring(2) : trimmed);
            }
        }
        return commands;
    }
}
