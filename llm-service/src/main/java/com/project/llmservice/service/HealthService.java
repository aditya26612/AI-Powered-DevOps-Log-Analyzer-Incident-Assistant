package com.project.llmservice.service;

import com.project.llmservice.dto.response.HealthResponse;

public interface HealthService {

    HealthResponse getHealth();

}