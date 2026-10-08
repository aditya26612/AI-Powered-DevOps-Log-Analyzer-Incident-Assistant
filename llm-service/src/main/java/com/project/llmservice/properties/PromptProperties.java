package com.project.llmservice.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "llm.prompt")
public class PromptProperties {

    private Integer maxContextLength;

}