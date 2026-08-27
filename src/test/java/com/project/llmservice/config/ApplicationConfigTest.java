package com.project.llmservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationConfigTest {

    @Test
    void shouldCreateClockBean() {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ApplicationConfig.class)) {

            Clock clock = context.getBean(Clock.class);

            assertNotNull(clock);
        }
    }
}