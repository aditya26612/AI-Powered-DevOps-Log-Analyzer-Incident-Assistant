package com.project.llmservice.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptConstantsTest {

    @Test
    void shouldContainPromptConstants() {

        assertTrue(
                PromptConstants.ROOT_CAUSE_ANALYSIS
                        .equals("ROOT_CAUSE_ANALYSIS")
        );

        assertTrue(
                PromptConstants.JSON_RESPONSE_FORMAT
                        .contains("\"summary\"")
        );

        assertTrue(
                PromptConstants.JSON_RESPONSE_FORMAT
                        .contains("\"rootCause\"")
        );

        assertTrue(
                PromptConstants.JSON_RESPONSE_FORMAT
                        .contains("\"severity\"")
        );

        assertTrue(
                PromptConstants.JSON_RESPONSE_FORMAT
                        .contains("\"recommendation\"")
        );

        assertTrue(
                PromptConstants.JSON_ONLY_INSTRUCTION
                        .contains("ONLY valid JSON")
        );
    }
}