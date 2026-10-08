package com.project.llmservice.controller;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.service.LlmAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LlmAnalysisController {

    private final LlmAnalysisService llmAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<LlmAnalysisResponse> analyze(
            @Valid @RequestBody LlmAnalysisRequest request) {

        LlmAnalysisResponse response =
                llmAnalysisService.analyze(request);

        return ResponseEntity.ok(response);
    }
}