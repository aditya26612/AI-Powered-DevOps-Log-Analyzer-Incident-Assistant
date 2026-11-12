package com.project.log_layer.notification.service.impl;

import com.project.log_layer.dto.response.analyzer.IncidentDiagnosis;
import com.project.log_layer.entity.Log;
import com.project.log_layer.notification.config.NotificationProperties;
import com.project.log_layer.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Composite notification service that dispatches incident alerts across all enabled channels.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class CompositeNotificationService implements NotificationService {

    private final List<NotificationService> delegates;
    private final NotificationProperties properties;

    @Override
    public void sendIncidentAlert(Log logEntity, IncidentDiagnosis diagnosis) {
        String service = logEntity.getServiceName() != null ? logEntity.getServiceName() : "unknown-service";
        String severity = diagnosis != null && diagnosis.getSeverity() != null
                ? diagnosis.getSeverity()
                : (logEntity.getLevel() != null ? logEntity.getLevel().name() : "ERROR");

        String rootCause = diagnosis != null && diagnosis.getProbableRootCause() != null
                ? diagnosis.getProbableRootCause()
                : "Investigating...";

        String fix = diagnosis != null && diagnosis.getSuggestedFix() != null
                ? diagnosis.getSuggestedFix()
                : "Inspect service logs and pod status.";

        // Structured console alert for DevOps observability
        log.warn("""
                
                ========================= [DEVINSIGHT INCIDENT ALERT] =========================
                [ALERT] Service:     {}
                [ALERT] Environment: {}
                [ALERT] Severity:    {}
                [ALERT] Log Message: {}
                [ALERT] Root Cause:  {}
                [ALERT] Suggested Fix: {}
                [ALERT] Anomaly:     {} (Decision Score: {})
                ==============================================================================
                """,
                service,
                logEntity.getEnvironment(),
                severity,
                logEntity.getMessage(),
                rootCause,
                fix,
                logEntity.getAnomaly(),
                logEntity.getDecisionScore()
        );

        // Delegate to channel-specific implementations (e.g. Slack, Email)
        for (NotificationService delegate : delegates) {
            if (delegate != this) {
                try {
                    delegate.sendIncidentAlert(logEntity, diagnosis);
                } catch (Exception ex) {
                    log.error("Notification delegate {} failed for logId={}: {}",
                            delegate.getClass().getSimpleName(), logEntity.getId(), ex.getMessage());
                }
            }
        }
    }
}
