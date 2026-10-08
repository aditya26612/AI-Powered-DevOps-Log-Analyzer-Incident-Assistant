package com.project.llmservice.prompt;

import com.project.llmservice.exception.PromptTemplateNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class PromptTemplateLoader {



    public String load(PromptType promptType) {

        Objects.requireNonNull(promptType, "PromptType must not be null");

        ClassPathResource resource =
                new ClassPathResource(promptType.getResourcePath());

        try {
            return new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new PromptTemplateNotFoundException(
                    "Failed to load prompt template: " + promptType.getResourcePath(),
                    e
            );
        }
    }
}