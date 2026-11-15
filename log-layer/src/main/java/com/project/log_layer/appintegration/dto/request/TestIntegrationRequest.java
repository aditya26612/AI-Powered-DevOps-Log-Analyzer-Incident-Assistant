package com.project.log_layer.appintegration.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestIntegrationRequest {
    private String applicationName;
    private String customLogMessage;
}

