package com.project.llmservice.provider.impl;

import com.project.llmservice.exception.ProviderException;
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

        if (prompt == null || prompt.isBlank()) {
            throw new ProviderException("Prompt must not be null or empty");
        }

        try {

            String response = chatClient
                    .prompt(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new ProviderException(
                        "LLM provider returned an empty response"
                );
            }
//
//            System.out.println("===== RAW OLLAMA RESPONSE =====");
//            System.out.println(response);
//            System.out.println("===============================");

            return response;



        } catch (ProviderException e) {

            throw e;

        } catch (Exception e) {

            throw new ProviderException(
                    "Failed to communicate with Ollama provider",
                    e
            );
        }
    }
}