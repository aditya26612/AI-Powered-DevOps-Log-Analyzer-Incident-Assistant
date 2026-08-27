package com.project.llmservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestClientConfigTest {

    @Test
    void shouldCreateRestClientBuilderBean() {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RestClientConfig.class)) {

            RestClient.Builder builder =
                    context.getBean(RestClient.Builder.class);

            assertNotNull(builder);
        }
    }
}