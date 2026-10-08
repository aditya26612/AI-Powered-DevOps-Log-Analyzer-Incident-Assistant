package com.project.llmservice.service.impl;

import com.project.llmservice.provider.ProviderFactory;
import com.project.llmservice.service.TestLlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestLlmServiceImpl implements TestLlmService {

    private final ProviderFactory providerFactory;

    @Override
    public String testConnection() {

        return providerFactory
                .getProvider()
                .generate("Say only the word READY.");
    }
}