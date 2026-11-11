package com.project.log_layer.service.impl;

import com.project.log_layer.dto.request.analyzer.LogInspectRequest;
import com.project.log_layer.dto.response.analyzer.LogInspectionResponse;
import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.enums.LogSource;
import com.project.log_layer.integration.client.LlmInferenceClient;
import com.project.log_layer.integration.client.MlInferenceClient;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import com.project.log_layer.integration.dto.LlmAnalysisResponse;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import com.project.log_layer.integration.dto.MlPredictionResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceInspectTest {

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
        lenient().when(httpServletRequest.getHeader("X-User-Email")).thenReturn("devops@company.com");
    }

    @Test
    void inspect_shouldReturnFastResponseWithoutLlm_whenFastModeRequested() {
        String logText = "2026-06-26T20:15:31.123+05:30 INFO 12345 --- [main] c.p.service.UserService : Normal ping";

        LogInspectRequest request = LogInspectRequest.builder()
                .log(logText)
                .appType(LogSource.SPRING_BOOT)
                .analysisMode("FAST")
                .applicationName("user-service")
                .build();

        ParsedLogData parsedData = ParsedLogData.builder()
                .timestamp(LocalDateTime.now())
                .logLevel(LogLevel.INFO)
                .message("Normal ping")
                .source(LogSource.SPRING_BOOT)
                .applicationName("user-service")
                .build();

        Log logEntity = Log.builder()
                .level(LogLevel.INFO)
                .message("Normal ping")
                .source(LogSource.SPRING_BOOT)
                .applicationName("user-service")
                .build();

        MlPredictionResponse mlResponse = MlPredictionResponse.builder()
                .prediction(1)
                .predictionLabel("NORMAL")
                .anomaly(false)
                .decisionScore(0.35)
                .modelVersion("1.0.0")
                .build();

        when(parserFactory.getParser(LogSource.SPRING_BOOT)).thenReturn(logParser);
        when(logParser.parse(any())).thenReturn(parsedData);
        when(parsedLogMapper.toEntity(parsedData)).thenReturn(logEntity);
        when(mlPredictionMapper.toRequest(any(Log.class))).thenReturn(mock(MlPredictionRequest.class));
        when(mlInferenceClient.predict(any())).thenReturn(mlResponse);

        LogInspectionResponse response = logService.inspect(request);

        assertNotNull(response);
        assertEquals(LogLevel.INFO, response.getLevel());
        assertFalse(response.isHasAnomaly());
        assertNull(response.getIncidentDiagnosis()); // FAST mode skips LLM
        verify(llmInferenceClient, never()).analyze(any());
        verify(logRepository).save(any(Log.class));
    }

    @Test
    void inspect_shouldTriggerLlmDiagnosis_whenAnomalyDetectedInAutoMode() {
        String logText = "2026-06-26T20:15:31.123+05:30 ERROR 12345 --- [main] o.s.b.Tomcat : Port 8080 in use\n\tat Tomcat.start()";

        LogInspectRequest request = LogInspectRequest.builder()
                .log(logText)
                .appType(LogSource.SPRING_BOOT)
                .analysisMode("AUTO")
                .applicationName("payment-service")
                .build();

        ParsedLogData parsedData = ParsedLogData.builder()
                .timestamp(LocalDateTime.now())
                .logLevel(LogLevel.ERROR)
                .message("Port 8080 in use\n\tat Tomcat.start()")
                .source(LogSource.SPRING_BOOT)
                .applicationName("payment-service")
                .build();

        Log logEntity = Log.builder()
                .level(LogLevel.ERROR)
                .message("Port 8080 in use\n\tat Tomcat.start()")
                .source(LogSource.SPRING_BOOT)
                .applicationName("payment-service")
                .build();

        MlPredictionResponse mlResponse = MlPredictionResponse.builder()
                .prediction(-1)
                .predictionLabel("ANOMALY")
                .anomaly(true)
                .decisionScore(-0.45)
                .modelVersion("1.0.0")
                .build();

        LlmAnalysisResponse llmResponse = new LlmAnalysisResponse(
                "Port 8080 conflict",
                "PortInUseException on 8080",
                "CRITICAL",
                "Kill conflicting process:\n$ netstat -ano | findstr :8080\n$ kill -9 <PID>"
        );

        when(parserFactory.getParser(LogSource.SPRING_BOOT)).thenReturn(logParser);
        when(logParser.parse(any())).thenReturn(parsedData);
        when(parsedLogMapper.toEntity(parsedData)).thenReturn(logEntity);
        when(mlPredictionMapper.toRequest(any(Log.class))).thenReturn(mock(MlPredictionRequest.class));
        when(mlInferenceClient.predict(any())).thenReturn(mlResponse);

        when(llmAnalysisMapper.toRequest(any(Log.class))).thenReturn(mock(LlmAnalysisRequest.class));
        when(llmInferenceClient.analyze(any())).thenReturn(llmResponse);

        LogInspectionResponse response = logService.inspect(request);

        assertNotNull(response);
        assertTrue(response.isHasAnomaly());
        assertTrue(response.isHasStackTrace());
        assertTrue(logEntity.getAnomaly(), "Anomaly flag should be persisted as true on -1 prediction");
        assertNotNull(response.getIncidentDiagnosis());
        assertEquals("Port 8080 conflict", response.getIncidentDiagnosis().getSummary());
        assertEquals("CRITICAL", response.getIncidentDiagnosis().getSeverity());
        assertFalse(response.getIncidentDiagnosis().getRemediationCommands().isEmpty());

        verify(llmInferenceClient).analyze(any());
        verify(logRepository).save(any(Log.class));
    }
}

