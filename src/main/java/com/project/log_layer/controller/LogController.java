package com.project.log_layer.controller;

import com.project.log_layer.dto.request.analysis.LogAnalysisRequest;
import com.project.log_layer.dto.request.ingest.BatchLogRequest;
import com.project.log_layer.dto.request.ingest.LogIngestRequest;
import com.project.log_layer.dto.request.search.LogFilterRequest;
import com.project.log_layer.dto.response.analysis.LogAnalysisResponse;
import com.project.log_layer.dto.response.common.BatchOperationResponse;
import com.project.log_layer.dto.response.common.PagedResponse;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Validated
public class LogController {

    private final LogService logService;

    /**
     * Ingest a single log.
     */
    @PostMapping
    public ResponseEntity<LogResponse> ingest(
            @Valid @RequestBody LogIngestRequest request) {

        LogResponse response = logService.ingest(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Ingest multiple logs.
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchOperationResponse> ingestBatch(
            @Valid @RequestBody BatchLogRequest request) {

        BatchOperationResponse response =
                logService.ingestBatch(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Get log by id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LogResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                logService.getById(id)
        );
    }

    /**
     * Search logs.
     */
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<LogResponse>> search(
            @Valid @RequestBody LogFilterRequest request) {

        PagedResponse<LogResponse> response =
                logService.search(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete log.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        logService.delete(id);

        return ResponseEntity.noContent().build();

    }

    /**
     * Analyze log using AI.
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<LogAnalysisResponse> analyze(
            @PathVariable Long id,
            @Valid @RequestBody LogAnalysisRequest request) {

        return ResponseEntity.ok(
                logService.analyze(id, request)
        );
    }

}