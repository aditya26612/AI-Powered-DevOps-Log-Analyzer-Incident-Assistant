package com.project.log_layer.appintegration.service;

import com.project.log_layer.appintegration.dto.request.CreateIntegrationRequest;
import com.project.log_layer.appintegration.dto.request.TestIntegrationRequest;
import com.project.log_layer.appintegration.dto.response.ApplicationIntegrationResponse;
import com.project.log_layer.appintegration.dto.response.TestIntegrationResponse;

import java.util.List;

public interface ApplicationIntegrationService {

    ApplicationIntegrationResponse createOrUpdateIntegration(CreateIntegrationRequest request, String userEmail);

    ApplicationIntegrationResponse getIntegration(String applicationName, String userEmail);

    List<ApplicationIntegrationResponse> getAllIntegrations(String userEmail);

    void deleteIntegration(Long id, String userEmail);

    TestIntegrationResponse runTestSimulation(TestIntegrationRequest request, String userEmail);
}

