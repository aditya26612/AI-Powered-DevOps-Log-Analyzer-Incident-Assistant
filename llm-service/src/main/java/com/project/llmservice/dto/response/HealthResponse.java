package com.project.llmservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HealthResponse {

    private String status;

    private String service;

    private String version;

    private String provider;

    private String model;

    private boolean modelLoaded;

    private LocalDateTime timestamp;

}