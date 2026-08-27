package com.project.llmservice.service;

import com.project.llmservice.dto.request.LlmAnalysisRequest;
import com.project.llmservice.dto.response.LlmAnalysisResponse;

public interface LlmAnalysisService {

    LlmAnalysisResponse analyze(LlmAnalysisRequest request);

}