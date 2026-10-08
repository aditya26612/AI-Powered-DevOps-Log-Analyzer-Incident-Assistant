package com.project.llmservice.constants;

public final class PromptConstants {

    private PromptConstants() {
    }

    public static final String ROOT_CAUSE_ANALYSIS =
            "ROOT_CAUSE_ANALYSIS";

    public static final String JSON_RESPONSE_FORMAT =
            """
            {
              "summary": "string",
              "rootCause": "string | null",
              "severity": "LOW | MEDIUM | HIGH | CRITICAL",
              "recommendation": "string | null"
            }
            """;

    public static final String JSON_ONLY_INSTRUCTION =
            "Return ONLY valid JSON matching the required schema.";
}