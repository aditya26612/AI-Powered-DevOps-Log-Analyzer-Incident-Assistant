package com.project.log_layer.service.impl;

import com.project.log_layer.dto.request.analysis.LogAnalysisRequest;
import jakarta.servlet.http.HttpServletRequest;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.response.analysis.LogAnalysisResponse;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.exception.InvalidSearchCriteriaException;
import com.project.log_layer.exception.LogNotFoundException;
import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.dto.MlPredictionRequest;
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
import com.project.log_layer.specification.LogSpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceImplTest {

    @Mock
    private LogSpecificationBuilder logSpecificationBuilder;

    @Mock
    private LogRepository logRepository;

    @Mock
    private ParserFactory parserFactory;

    @Mock
    private ParsedLogMapper parsedLogMapper;

    @Mock
    private LogMapper logMapper;

    @Mock
    private MlInferenceClient mlInferenceClient;

    @Mock
    private MlPredictionMapper mlPredictionMapper;

    @Mock
    private LogParser logParser;

    @Mock
    private LlmInferenceClient llmInferenceClient;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private LlmAnalysisMapper llmAnalysisMapper;

    @Mock
    private LlmAnalysisFailureService llmAnalysisFailureService;

    @InjectMocks
    private LogServiceImpl logService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(httpServletRequest.getHeader("X-User-Email"))
                .thenReturn("test@example.com");
    }

    @Test
    void getById_shouldReturnLogResponse_whenLogExists() {

        Long id = 1L;

        Log log = Log.builder()
                .id(id)
                .correlationId(UUID.randomUUID())
                .applicationName("payment-service")
                .userEmail("test@example.com")
                .timestamp(LocalDateTime.now())
                .level(LogLevel.ERROR)
                .serviceName("payment-service")
                .message("Database connection failed")
                .rawLog("ERROR Database connection failed")
                .source(LogSource.SPRING_BOOT)
                .environment(Environment.DEVELOPMENT)
                .build();

        LogResponse expectedResponse = new LogResponse();

        when(logRepository.findById(id))
                .thenReturn(Optional.of(log));

        when(logMapper.toLogResponse(log))
                .thenReturn(expectedResponse);

        LogResponse result = logService.getById(id);

        assertSame(expectedResponse, result);

        verify(logRepository).findById(id);
        verify(logMapper).toLogResponse(log);
    }

    @Test
    void analyze_shouldReturnLlmAnalysis_whenAnalysisSucceeds() {

        Long id = 1L;

        LocalDateTime mlAnalyzedAt =
                LocalDateTime.of(2026, 10, 5, 10, 5);

        Log log = Log.builder()
                .id(id)
                .applicationName("payment-service")
                .userEmail("test@example.com")
                .serviceName("payment-service")
                .timestamp(LocalDateTime.of(2026, 10, 5, 10, 0))
                .level(LogLevel.ERROR)
                .message("Database connection failed")
                .prediction(1)
                .predictionLabel("DATABASE_FAILURE")
                .decisionScore(0.95)
                .modelVersion("1.0.0")
                .analysisStatus(AnalysisStatus.COMPLETED)
                .analyzedAt(mlAnalyzedAt)
                .build();

        LogAnalysisRequest request = LogAnalysisRequest.builder()
                .model("phi3:mini")
                .analysisTypes(
                        java.util.EnumSet.of(
                                com.project.log_layer.enums.AnalysisType.SUMMARY,
                                com.project.log_layer.enums.AnalysisType.ROOT_CAUSE,
                                com.project.log_layer.enums.AnalysisType.SUGGESTED_FIX
                        )
                )
                .build();

        LlmAnalysisRequest llmRequest = LlmAnalysisRequest.builder()
                .timestamp(log.getTimestamp().toString())
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .prediction(1)
                .predictionLabel("DATABASE_FAILURE")
                .decisionScore(0.95)
                .modelVersion("1.0.0")
                .build();

        LlmAnalysisResponse llmResponse =
                LlmAnalysisResponse.builder()
                        .summary("Database connection failed.")
                        .rootCause("PostgreSQL database is unreachable.")
                        .severity("CRITICAL")
                        .recommendation(
                                "Verify database availability, network connectivity, and database credentials."
                        )
                        .build();

        LogResponse expectedLogResponse = new LogResponse();

        when(logRepository.findById(id))
                .thenReturn(Optional.of(log));

        when(llmAnalysisMapper.toRequest(log))
                .thenReturn(llmRequest);

        when(llmInferenceClient.analyze(llmRequest))
                .thenReturn(llmResponse);

        when(logRepository.save(log))
                .thenReturn(log);

        when(logMapper.toLogResponse(log))
                .thenReturn(expectedLogResponse);

        LogAnalysisResponse result =
                logService.analyze(id, request);

        assertSame(expectedLogResponse, result.getLog());

        assertEquals(
                "Database connection failed.",
                result.getSummary()
        );

        assertEquals(
                "PostgreSQL database is unreachable.",
                result.getProbableRootCause()
        );

        assertEquals(
                "Verify database availability, network connectivity, and database credentials.",
                result.getSuggestedFix()
        );

        assertEquals(
                AnalysisStatus.COMPLETED,
                log.getLlmAnalysisStatus()
        );

        assertNotNull(log.getLlmAnalyzedAt());

        // ML lifecycle must remain untouched.
        assertEquals(
                AnalysisStatus.COMPLETED,
                log.getAnalysisStatus()
        );

        assertEquals(
                mlAnalyzedAt,
                log.getAnalyzedAt()
        );

        verify(logRepository).findById(id);
        verify(llmAnalysisMapper).toRequest(log);
        verify(llmInferenceClient).analyze(llmRequest);
        verify(logRepository).save(log);
        verify(logMapper).toLogResponse(log);

        // Failure service must not be called on successful analysis.
        verifyNoInteractions(llmAnalysisFailureService);
    }

    @Test
    void analyze_shouldMarkLlmAnalysisFailed_whenLlmFails() {

        Long id = 1L;

        LocalDateTime mlAnalyzedAt =
                LocalDateTime.of(2026, 10, 5, 10, 5);

        Log log = Log.builder()
                .id(id)
                .applicationName("payment-service")
                .userEmail("test@example.com")
                .serviceName("payment-service")
                .timestamp(LocalDateTime.of(2026, 10, 5, 10, 0))
                .level(LogLevel.ERROR)
                .message("Database connection failed")
                .prediction(1)
                .predictionLabel("DATABASE_FAILURE")
                .decisionScore(0.95)
                .modelVersion("1.0.0")
                .analysisStatus(AnalysisStatus.COMPLETED)
                .analyzedAt(mlAnalyzedAt)
                .build();

        LogAnalysisRequest request = LogAnalysisRequest.builder()
                .model("phi3:mini")
                .analysisTypes(
                        java.util.EnumSet.of(
                                com.project.log_layer.enums.AnalysisType.ROOT_CAUSE
                        )
                )
                .build();

        LlmAnalysisRequest llmRequest = LlmAnalysisRequest.builder()
                .timestamp(log.getTimestamp().toString())
                .level("ERROR")
                .serviceName("payment-service")
                .message("Database connection failed")
                .prediction(1)
                .predictionLabel("DATABASE_FAILURE")
                .decisionScore(0.95)
                .modelVersion("1.0.0")
                .build();

        when(logRepository.findById(id))
                .thenReturn(Optional.of(log));

        when(llmAnalysisMapper.toRequest(log))
                .thenReturn(llmRequest);

        when(llmInferenceClient.analyze(llmRequest))
                .thenThrow(
                        new LlmServiceException(
                                "LLM Service unavailable."
                        )
                );

        assertThrows(
                LlmServiceException.class,
                () -> logService.analyze(id, request)
        );

        // LLM failure lifecycle is delegated to the independent failure service.
        verify(llmAnalysisFailureService).markAsFailed(log);

        // ML lifecycle must remain untouched.
        assertEquals(
                AnalysisStatus.COMPLETED,
                log.getAnalysisStatus()
        );

        assertEquals(
                mlAnalyzedAt,
                log.getAnalyzedAt()
        );

        verify(logRepository).findById(id);
        verify(llmAnalysisMapper).toRequest(log);
        verify(llmInferenceClient).analyze(llmRequest);

        // LogServiceImpl no longer directly saves the failed lifecycle.
        verify(logRepository, never()).save(log);

        verify(logMapper, never()).toLogResponse(any());
    }

    @Test
    void analyze_shouldThrowException_whenLogDoesNotExist() {

        Long id = 999L;

        LogAnalysisRequest request = LogAnalysisRequest.builder()
                .model("phi3:mini")
                .analysisTypes(
                        java.util.EnumSet.of(
                                com.project.log_layer.enums.AnalysisType.ROOT_CAUSE
                        )
                )
                .build();

        when(logRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                LogNotFoundException.class,
                () -> logService.analyze(id, request)
        );

        verify(logRepository).findById(id);

        verifyNoInteractions(
                llmAnalysisMapper,
                llmInferenceClient,
                logMapper,
                llmAnalysisFailureService
        );
    }

    @Test
    void getById_shouldThrowException_whenLogDoesNotExist() {

        Long id = 999L;

        when(logRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                LogNotFoundException.class,
                () -> logService.getById(id)
        );

        verify(logRepository).findById(id);
        verify(logMapper, never()).toLogResponse(any());
    }

    @Test
    void delete_shouldDeleteLog_whenLogExists() {

        Long id = 1L;

        Log log = Log.builder()
                .id(id)
                .userEmail("test@example.com")
                .build();

        when(logRepository.findById(id))
                .thenReturn(Optional.of(log));

        logService.delete(id);

        verify(logRepository).findById(id);
        verify(logRepository).delete(log);
    }

    @Test
    void delete_shouldThrowException_whenLogDoesNotExist() {

        Long id = 999L;

        when(logRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                LogNotFoundException.class,
                () -> logService.delete(id)
        );

        verify(logRepository).findById(id);
        verify(logRepository, never()).delete(any(Log.class));
    }

    @Test
    void exists_shouldReturnRepositoryResult() {

        Long id = 1L;

        Log log = Log.builder()
                .id(id)
                .userEmail("test@example.com")
                .build();

        when(logRepository.findById(id))
                .thenReturn(Optional.of(log));

        boolean result = logService.exists(id);

        assertTrue(result);

        verify(logRepository).findById(id);
    }


    @Test
    void search_shouldRejectNullRequest() {

        assertThrows(
                InvalidSearchCriteriaException.class,
                () -> logService.search(null)
        );

        verifyNoInteractions(
                logRepository,
                logSpecificationBuilder
        );
    }

    @Test
    void search_shouldRejectInvalidTimeRange() {

        var request =
                new com.project.log_layer.dto.request.search.LogFilterRequest();

        request.setStartTime(
                LocalDateTime.of(2026, 1, 10, 12, 0)
        );

        request.setEndTime(
                LocalDateTime.of(2026, 1, 9, 12, 0)
        );

        assertThrows(
                InvalidSearchCriteriaException.class,
                () -> logService.search(request)
        );

        verifyNoInteractions(
                logRepository,
                logSpecificationBuilder
        );
    }

    @Test
    void ingest_shouldMarkMlAnalysisFailed_whenMlFails() {

        UUID correlationId = UUID.randomUUID();

        LogIngestRequest request = LogIngestRequest.builder()
                .rawLog(
                        "2026-10-05 10:00:00 ERROR payment-service - Database connection failed"
                )
                .source(LogSource.SPRING_BOOT)
                .environment(Environment.DEVELOPMENT)
                .applicationName("payment-service")
                .hostName("localhost")
                .correlationId(correlationId)
                .build();

        ParsedLogData parsedData = ParsedLogData.builder()
                .timestamp(
                        LocalDateTime.of(2026, 10, 5, 10, 0)
                )
                .logLevel(LogLevel.ERROR)
                .message("Database connection failed")
                .source(LogSource.SPRING_BOOT)
                .applicationName("payment-service")
                .serviceName("payment-service")
                .hostName("localhost")
                .environment(Environment.DEVELOPMENT)
                .rawLog(request.getRawLog())
                .build();

        Log log = Log.builder()
                .timestamp(parsedData.getTimestamp())
                .level(parsedData.getLogLevel())
                .message(parsedData.getMessage())
                .source(parsedData.getSource())
                .applicationName(parsedData.getApplicationName())
                .serviceName(parsedData.getServiceName())
                .hostName(parsedData.getHostName())
                .environment(parsedData.getEnvironment())
                .rawLog(parsedData.getRawLog())
                .build();

        MlPredictionRequest mlRequest =
                MlPredictionRequest.builder()
                        .timestamp(parsedData.getTimestamp().toString())
                        .level("ERROR")
                        .serviceName("payment-service")
                        .message("Database connection failed")
                        .build();

        LogResponse expectedResponse = new LogResponse();

        when(parserFactory.getParser(request.getSource()))
                .thenReturn(logParser);

        when(logParser.parse(request))
                .thenReturn(parsedData);

        when(parsedLogMapper.toEntity(parsedData))
                .thenReturn(log);

        when(mlPredictionMapper.toRequest(log))
                .thenReturn(mlRequest);

        when(mlInferenceClient.predict(mlRequest))
                .thenThrow(
                        new MlServiceException(
                                "ML Service unavailable."
                        )
                );

        when(logRepository.save(log))
                .thenReturn(log);

        when(logMapper.toLogResponse(log))
                .thenReturn(expectedResponse);

        LogResponse result =
                logService.ingest(request);

        assertSame(expectedResponse, result);

        assertEquals(
                correlationId,
                log.getCorrelationId()
        );

        assertEquals(
                AnalysisStatus.FAILED,
                log.getAnalysisStatus()
        );

        assertNotNull(log.getAnalyzedAt());

        verify(mlInferenceClient).predict(mlRequest);
        verify(logRepository).save(log);
        verify(logMapper).toLogResponse(log);
    }
}