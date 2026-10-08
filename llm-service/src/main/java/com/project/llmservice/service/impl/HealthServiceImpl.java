package com.project.llmservice.service.impl;

import com.project.llmservice.dto.response.HealthResponse;
import com.project.llmservice.properties.ApplicationProperties;
import com.project.llmservice.properties.OllamaProperties;
import com.project.llmservice.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private final ApplicationProperties applicationProperties;
    private final OllamaProperties ollamaProperties;

    @Override
    public HealthResponse getHealth() {

        return HealthResponse.builder()
                .status("UP")
                .service("DevInsight LLM Service")
                .version("1.0.0")
                .provider(applicationProperties.getProvider())
                .model(ollamaProperties.getChat().getOptions().getModel())
                .modelLoaded(false)
                .timestamp(LocalDateTime.now())
                .build();
    }
}