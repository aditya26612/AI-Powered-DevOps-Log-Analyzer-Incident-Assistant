package com.project.log_layer.appintegration.controller;

import com.project.log_layer.appintegration.dto.request.CreateIntegrationRequest;
import com.project.log_layer.appintegration.dto.request.TestIntegrationRequest;
import com.project.log_layer.appintegration.dto.response.ApplicationIntegrationResponse;
import com.project.log_layer.appintegration.dto.response.TestIntegrationResponse;
import com.project.log_layer.appintegration.service.ApplicationIntegrationService;
import com.project.log_layer.util.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class ApplicationIntegrationController {

    private final ApplicationIntegrationService integrationService;

    @PostMapping
    public ResponseEntity<ApplicationIntegrationResponse> createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            HttpServletRequest servletRequest) {

        String userEmail = CurrentUser.getEmail(servletRequest);
        ApplicationIntegrationResponse response = integrationService.createOrUpdateIntegration(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationIntegrationResponse>> getAllIntegrations(
            HttpServletRequest servletRequest) {

        String userEmail = CurrentUser.getEmail(servletRequest);
        return ResponseEntity.ok(integrationService.getAllIntegrations(userEmail));
    }

    @GetMapping("/{name}")
    public ResponseEntity<ApplicationIntegrationResponse> getIntegration(
            @PathVariable String name,
            HttpServletRequest servletRequest) {

        String userEmail = CurrentUser.getEmail(servletRequest);
        return ResponseEntity.ok(integrationService.getIntegration(name, userEmail));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(
            @PathVariable Long id,
            HttpServletRequest servletRequest) {

        String userEmail = CurrentUser.getEmail(servletRequest);
        integrationService.deleteIntegration(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<TestIntegrationResponse> runTestSimulation(
            @RequestBody TestIntegrationRequest request,
            HttpServletRequest servletRequest) {

        String userEmail = CurrentUser.getEmail(servletRequest);
        TestIntegrationResponse response = integrationService.runTestSimulation(request, userEmail);
        return ResponseEntity.ok(response);
    }
}

