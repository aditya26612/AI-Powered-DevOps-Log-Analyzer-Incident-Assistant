package com.project.llmservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponse {

    private final String code;

    private final String message;

    private final LocalDateTime timestamp;
}