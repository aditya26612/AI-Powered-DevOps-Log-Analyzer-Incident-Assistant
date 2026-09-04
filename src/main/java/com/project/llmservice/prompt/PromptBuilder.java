package com.project.llmservice.prompt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;



@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final PromptTemplateLoader promptTemplateLoader;
    private final PromptValidator promptValidator;

    public String build(PromptType promptType, PromptContext context) {

        String template = promptTemplateLoader.load(promptType);

        String prompt = template
                .replace("${timestamp}", context.getTimestamp())
                .replace("${level}", context.getLevel())
                .replace("${serviceName}", context.getServiceName())
                .replace("${message}", context.getMessage())
                .replace("${retrievedContext}", context.getRetrievedContext());

        promptValidator.validate(prompt);

        return prompt;
    }
}