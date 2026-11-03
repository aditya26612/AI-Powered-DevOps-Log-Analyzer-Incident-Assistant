
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

            String cleanedResponse = removeMarkdownCodeFences(response);

            return objectMapper.readValue(
                    cleanedResponse,
                    LlmAnalysisResponse.class
            );

        } catch (JsonProcessingException e) {

            throw new ResponseParsingException(
                    "Failed to parse LLM response as JSON",
                    e
            );
        }
    }

    private String removeMarkdownCodeFences(String response) {

        String cleaned = response.trim();

        // Remove opening Markdown code fence.
        if (cleaned.startsWith("```")) {

            int firstNewLine = cleaned.indexOf('\n');

            if (firstNewLine == -1) {
                throw new ResponseParsingException(
                        "Invalid Markdown code fence in LLM response"
                );
            }

            cleaned = cleaned.substring(firstNewLine + 1).trim();

            // Remove closing Markdown code fence.
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(
                        0,
                        cleaned.length() - 3
                ).trim();
            }
        }

        return cleaned;
    }
}
