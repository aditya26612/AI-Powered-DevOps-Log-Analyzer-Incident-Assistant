package com.project.log_layer.controller;

import com.project.log_layer.dto.request.analyzer.LogInspectRequest;
import com.project.log_layer.dto.response.analyzer.LogInspectionResponse;
import com.project.log_layer.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated Industrial Log Analyzer Controller.
 *
 * Exposes the unified endpoint for on-demand log inspection,
 * anomaly detection, and incident root-cause diagnosis.
 */
@RestController
@RequestMapping("/api/v1/analyzer")
@RequiredArgsConstructor
@Validated
public class LogAnalyzerController {

    private final LogService logService;

    /**
     * Inspect and diagnose a log or stack trace.
     */
    @PostMapping("/inspect")
    public ResponseEntity<LogInspectionResponse> inspect(
            @Valid @RequestBody LogInspectRequest request) {

        return ResponseEntity.ok(
                logService.inspect(request)
        );
    }

}

