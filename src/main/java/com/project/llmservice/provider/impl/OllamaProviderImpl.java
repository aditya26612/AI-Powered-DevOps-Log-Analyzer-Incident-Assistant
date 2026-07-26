package com.project.llmservice.provider.impl;

import com.project.llmservice.provider.OllamaProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class OllamaProviderImpl implements OllamaProvider {

    private final ChatClient chatClient;

    public OllamaProviderImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String prompt) {

        return chatClient
                .prompt(prompt)
                .call()
                .content();
    }
}