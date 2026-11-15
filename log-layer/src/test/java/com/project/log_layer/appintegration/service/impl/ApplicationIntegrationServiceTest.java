package com.project.log_layer.appintegration.service.impl;

import com.project.log_layer.appintegration.dto.request.CreateIntegrationRequest;
import com.project.log_layer.appintegration.dto.response.ApplicationIntegrationResponse;
import com.project.log_layer.appintegration.entity.ApplicationIntegration;
import com.project.log_layer.appintegration.repository.ApplicationIntegrationRepository;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import com.project.log_layer.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationIntegrationServiceTest {

    @Mock
    private ApplicationIntegrationRepository integrationRepository;

    @Mock
    private LogService logService;

    @InjectMocks
    private ApplicationIntegrationServiceImpl integrationService;

    private CreateIntegrationRequest request;

    @BeforeEach
    void setUp() {
        request = CreateIntegrationRequest.builder()
                .applicationName("order-service")
                .techStack(TechStack.SPRING_BOOT)
                .environment(Environment.PRODUCTION)
                .slackWebhookUrl("https://hooks.slack.com/services/test/webhook")
                .slackChannelName("#all-devinsight")
                .alertOnAnomaly(true)
                .alertOnError(true)
                .description("Order processing backend")
                .build();
    }

    @Test
    @DisplayName("Should create application integration and generate code snippet")
    void shouldCreateApplicationIntegrationSuccessfully() {
        when(integrationRepository.findByApplicationName("order-service")).thenReturn(Optional.empty());
        when(integrationRepository.save(any(ApplicationIntegration.class))).thenAnswer(invocation -> {
            ApplicationIntegration entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        ApplicationIntegrationResponse response =
                integrationService.createOrUpdateIntegration(request, "aditya@iiitn.ac.in");

        assertThat(response).isNotNull();
        assertThat(response.getApplicationName()).isEqualTo("order-service");
        assertThat(response.getTechStack()).isEqualTo(TechStack.SPRING_BOOT);
        assertThat(response.getCodeSnippet()).contains("DevInsightLogbackAppender");
        assertThat(response.getSlackChannelName()).isEqualTo("#all-devinsight");

        verify(integrationRepository, times(1)).save(any(ApplicationIntegration.class));
    }

    @Test
    @DisplayName("Should generate Python snippet when techStack is PYTHON")
    void shouldGeneratePythonSnippet() {
        request.setTechStack(TechStack.PYTHON);
        when(integrationRepository.findByApplicationName("order-service")).thenReturn(Optional.empty());
        when(integrationRepository.save(any(ApplicationIntegration.class))).thenAnswer(invocation -> {
            ApplicationIntegration entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });

        ApplicationIntegrationResponse response =
                integrationService.createOrUpdateIntegration(request, "aditya@iiitn.ac.in");

        assertThat(response.getCodeSnippet()).contains("DevInsightHandler");
        assertThat(response.getTechStack()).isEqualTo(TechStack.PYTHON);
    }
}

