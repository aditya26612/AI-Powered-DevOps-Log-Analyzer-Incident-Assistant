package com.project.llmservice.controller;

import com.project.llmservice.dto.response.HealthResponse;
import com.project.llmservice.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {

        return ResponseEntity.ok(healthService.getHealth());

    }

}