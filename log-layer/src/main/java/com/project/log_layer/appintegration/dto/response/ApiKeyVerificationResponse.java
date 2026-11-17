package com.project.log_layer.appintegration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyVerificationResponse {
    private boolean valid;
    private String applicationName;
    private String userEmail;
}
