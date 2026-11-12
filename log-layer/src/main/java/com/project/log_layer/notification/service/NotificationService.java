package com.project.log_layer.notification.service;

import com.project.log_layer.dto.response.analyzer.IncidentDiagnosis;
import com.project.log_layer.entity.Log;

/**
 * Service interface for dispatching incident alerts across configured notification channels.
 */
public interface NotificationService {

    /**
     * Sends an incident alert for a diagnosed log event.
     *
     * @param logEntity the persisted log entity
     * @param diagnosis the LLM-generated incident diagnosis
     */
    void sendIncidentAlert(Log logEntity, IncidentDiagnosis diagnosis);
}

