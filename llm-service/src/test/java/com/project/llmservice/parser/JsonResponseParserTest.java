package com.project.llmservice.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.llmservice.dto.response.LlmAnalysisResponse;
import com.project.llmservice.exception.ResponseParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonResponseParserTest {

    private JsonResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonResponseParser(new ObjectMapper());
    }

    @Test
    void shouldParseValidJsonResponse() {

        String json = """
                {
                    "summary": "Database connection failed.",
                    "rootCause": "PostgreSQL is unreachable.",
                    "severity": "CRITICAL",
                    "recommendation": "Verify database availability."
                }
                """;

        LlmAnalysisResponse response = parser.parse(json);

        assertNotNull(response);
        assertEquals("Database connection failed.", response.getSummary());
        assertEquals("PostgreSQL is unreachable.", response.getRootCause());
        assertEquals("CRITICAL", response.getSeverity());
        assertEquals(
                "Verify database availability.",
                response.getRecommendation()
        );
    }

    @Test
    void shouldThrowExceptionForEmptyResponse() {

        assertThrows(
                ResponseParsingException.class,
                () -> parser.parse("")
        );
    }

    @Test
    void shouldThrowExceptionForInvalidJson() {

        String invalidJson = """
                {
                    "summary": "Database connection failed.",
                    "severity":
                }
                """;

        assertThrows(
                ResponseParsingException.class,
                () -> parser.parse(invalidJson)
        );
    }
}