package com.project.llmservice.controller;

import com.project.llmservice.service.TestLlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final TestLlmService testLlmService;

    @GetMapping("/test")
    public String test() {

        return testLlmService.testConnection();

    }

}