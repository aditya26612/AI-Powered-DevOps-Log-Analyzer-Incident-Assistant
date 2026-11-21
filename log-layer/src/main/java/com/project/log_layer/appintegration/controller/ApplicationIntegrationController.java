package com.project.log_layer.appintegration.controller;

import com.project.log_layer.appintegration.dto.response.ApiKeyVerificationResponse;
import com.project.log_layer.tenant.ApplicationCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class ApplicationIntegrationController {

    private final ApplicationCredentials credentials;

    @GetMapping("/verify-key")
    public ResponseEntity<ApiKeyVerificationResponse> verifyApiKey(
            @RequestHeader(value = "X-API-Key", required = false) String headerApiKey) {
        if (headerApiKey == null || headerApiKey.isBlank()) {
            return ResponseEntity.badRequest().body(ApiKeyVerificationResponse.builder().valid(false).build());
        }
        return ResponseEntity.ok(ApiKeyVerificationResponse.builder()
                .valid(credentials.authenticate(headerApiKey).isPresent()).build());
    }
}
