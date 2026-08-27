package com.project.llmservice.parser;

import com.project.llmservice.dto.response.LlmAnalysisResponse;

public interface ResponseParser {

    LlmAnalysisResponse parse(String response);

}