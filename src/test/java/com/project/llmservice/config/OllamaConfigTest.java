package com.project.llmservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaConfigTest {

    @Test
    void shouldCreateChatClientBean() {

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);

        when(builder.build())
                .thenReturn(chatClient);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.registerBean(
                    ChatClient.Builder.class,
                    () -> builder
            );

            context.register(OllamaConfig.class);

            context.refresh();

            ChatClient result =
                    context.getBean(ChatClient.class);

            assertNotNull(result);
        }
    }
}