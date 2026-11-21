package com.project.log_layer.mapper;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MlPredictionMapper Regression Tests")
class MlPredictionMapperTest {

    private final MlPredictionMapper mapper = Mappers.getMapper(MlPredictionMapper.class);

    @Test
    @DisplayName("Should map serviceName when serviceName is present on Log entity")
    void shouldMapServiceName_whenServiceNamePresent() {
        Log log = Log.builder()
                .applicationName("DevInsightApp")
                .serviceName("payment-service")
                .level(LogLevel.ERROR)
                .message("Hikari connection pool exhausted")
                .timestamp(LocalDateTime.of(2026, 7, 20, 18, 0, 0))
                .build();

        MlPredictionRequest request = mapper.toRequest(log);

        assertNotNull(request);
        assertEquals("payment-service", request.serviceName(),
                "serviceName must map from log.serviceName, not applicationName");
        assertEquals("ERROR", request.level());
        assertEquals("Hikari connection pool exhausted", request.message());
        assertEquals(java.time.Instant.parse("2026-07-20T18:00:00Z"), java.time.Instant.parse(request.timestamp()));
    }

    @Test
    @DisplayName("Should fallback to applicationName when serviceName is null or blank")
    void shouldFallbackToApplicationName_whenServiceNameMissing() {
        Log log = Log.builder()
                .applicationName("DevInsightApp")
                .serviceName(null)
                .level(LogLevel.INFO)
                .message("Service startup complete")
                .timestamp(LocalDateTime.of(2026, 7, 20, 18, 0, 0))
                .build();

        MlPredictionRequest request = mapper.toRequest(log);

        assertNotNull(request);
        assertEquals("DevInsightApp", request.serviceName(),
                "serviceName should fallback to applicationName when serviceName is null");

        Log logBlank = Log.builder()
                .applicationName("DevInsightApp")
                .serviceName("   ")
                .level(LogLevel.INFO)
                .message("Service startup complete")
                .timestamp(LocalDateTime.of(2026, 7, 20, 18, 0, 0))
                .build();

        MlPredictionRequest requestBlank = mapper.toRequest(logBlank);
        assertEquals("DevInsightApp", requestBlank.serviceName(),
                "serviceName should fallback to applicationName when serviceName is blank");
    }

    @Test
    @DisplayName("Should return null when input log is null")
    void shouldReturnNull_whenLogIsNull() {
        assertNull(mapper.toRequest(null));
    }
}
