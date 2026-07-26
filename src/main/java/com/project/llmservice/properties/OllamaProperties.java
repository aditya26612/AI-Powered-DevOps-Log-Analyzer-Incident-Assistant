package com.project.llmservice.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "spring.ai.ollama")
public class OllamaProperties {

    private String baseUrl;

    private Chat chat = new Chat();

    @Getter
    @Setter
    public static class Chat {

        private Options options = new Options();

        @Getter
        @Setter
        public static class Options {

            private String model;

            private Double temperature;
        }
    }
}