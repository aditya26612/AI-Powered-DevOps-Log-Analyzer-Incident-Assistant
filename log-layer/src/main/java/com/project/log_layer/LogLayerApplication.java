package com.project.log_layer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LogLayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogLayerApplication.class, args);
    }

}
