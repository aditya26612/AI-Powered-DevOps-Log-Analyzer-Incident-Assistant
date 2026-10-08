package com.project.llmservice.prompt;

import com.project.llmservice.exception.PromptValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PromptValidator {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{[^}]+}");

    public void validate(String prompt) {

        if (prompt == null) {
            throw new PromptValidationException("Prompt must not be null.");
        }

        if (prompt.isBlank()) {
            throw new PromptValidationException("Prompt must not be blank.");
        }

        if (PLACEHOLDER_PATTERN.matcher(prompt).find()) {
            throw new PromptValidationException(
                    "Prompt contains unresolved placeholders."
            );
        }
    }
}