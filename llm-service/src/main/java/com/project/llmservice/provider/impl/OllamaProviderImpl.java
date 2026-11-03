
package com.project.llmservice.provider.impl;

import com.project.llmservice.exception.ProviderException;
import com.project.llmservice.provider.OllamaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class OllamaProviderImpl implements OllamaProvider {

    private static final Logger logger =
            LoggerFactory.getLogger(OllamaProviderImpl.class);

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

            logger.debug("===== RAW OLLAMA RESPONSE =====");
            logger.debug("{}", response);
            logger.debug("================================");

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
