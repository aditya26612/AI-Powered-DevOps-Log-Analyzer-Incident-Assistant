package com.project.llmservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.retrieval")
public class RagProperties {

    private double similarityThreshold = 0.70;
}