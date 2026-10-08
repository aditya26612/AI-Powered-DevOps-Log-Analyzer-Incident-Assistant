package com.project.llmservice.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateLoaderTest {

    private final PromptTemplateLoader loader =
            new PromptTemplateLoader();

    @Test
    void shouldLoadRootCauseAnalysisTemplate() {

        String template =
                loader.load(PromptType.ROOT_CAUSE_ANALYSIS);

        assertNotNull(template);
        assertFalse(template.isBlank());
    }

    @Test
    void shouldRejectNullPromptType() {

        assertThrows(
                NullPointerException.class,
                () -> loader.load(null)
        );
    }
}