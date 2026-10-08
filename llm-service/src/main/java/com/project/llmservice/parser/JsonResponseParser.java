package com.project.llmservice.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.exception.ResponseParsingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonResponseParser implements ResponseParser {

    private final ObjectMapper objectMapper;

    @Override
    public LlmAnalysisResponse parse(String response) {

        if (response == null || response.isBlank()) {
            throw new ResponseParsingException(
                    "LLM response is null or empty"
            );
        }

        try {

            return objectMapper.readValue(
                    response,
                    LlmAnalysisResponse.class
            );

        } catch (JsonProcessingException e) {

            throw new ResponseParsingException(
                    "Failed to parse LLM response as JSON",
                    e
            );
        }
    }
}