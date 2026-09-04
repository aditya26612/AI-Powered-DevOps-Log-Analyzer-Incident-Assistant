package com.project.llmservice.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmAnalysisRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void shouldAcceptValidRequest() {

        LlmAnalysisRequest request =
                LlmAnalysisRequest.builder()
                        .timestamp("2026-08-27T18:00:00")
                        .level("ERROR")
                        .serviceName("payment-service")
                        .message("Database connection failed")
                        .build();

        Set<ConstraintViolation<LlmAnalysisRequest>> violations =
                validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldRejectBlankFields() {

        LlmAnalysisRequest request =
                LlmAnalysisRequest.builder()
                        .timestamp("")
                        .level("")
                        .serviceName("")
                        .message("")
                        .build();

        Set<ConstraintViolation<LlmAnalysisRequest>> violations =
                validator.validate(request);

        assertEquals(4, violations.size());
    }

    @Test
    void shouldRejectNullFields() {

        LlmAnalysisRequest request =
                LlmAnalysisRequest.builder()
                        .timestamp(null)
                        .level(null)
                        .serviceName(null)
                        .message(null)
                        .build();

        Set<ConstraintViolation<LlmAnalysisRequest>> violations =
                validator.validate(request);

        assertEquals(4, violations.size());
    }
}