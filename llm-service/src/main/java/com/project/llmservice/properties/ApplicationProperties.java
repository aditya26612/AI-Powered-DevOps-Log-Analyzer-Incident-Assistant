package com.project.llmservice.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "llm")
public class ApplicationProperties {

    private String provider;

//    private Timeout timeout = new Timeout();
//
//    @Getter
//    @Setter
//    public static class Timeout {
//        private int seconds;
//    }
}