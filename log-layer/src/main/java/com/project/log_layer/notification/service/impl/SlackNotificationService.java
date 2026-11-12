package com.project.log_layer.notification.service.impl;

import com.project.log_layer.dto.response.analyzer.IncidentDiagnosis;
import com.project.log_layer.entity.Log;
import com.project.log_layer.notification.config.NotificationProperties;
import com.project.log_layer.notification.dto.SlackPayload;
import com.project.log_layer.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches rich incident alerts to Slack channels via Incoming Webhooks.
 */
@Slf4j
@Service
public class SlackNotificationService implements NotificationService {

    private final NotificationProperties properties;
    private final WebClient webClient;

    public SlackNotificationService(
            NotificationProperties properties,
            WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public void sendIncidentAlert(Log logEntity, IncidentDiagnosis diagnosis) {
        NotificationProperties.SlackProperties slackProps = properties.getSlack();

        if (!slackProps.isEnabled() || slackProps.getWebhookUrl() == null || slackProps.getWebhookUrl().isBlank()) {
            log.debug("Slack notifications disabled or webhook URL not configured. Skipping Slack alert.");
            return;
        }

        try {
            SlackPayload payload = buildSlackPayload(logEntity, diagnosis, slackProps);

            log.info("Dispatching incident alert to Slack webhook for service={}", logEntity.getServiceName());

            webClient.post()
                    .uri(slackProps.getWebhookUrl())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(resp -> log.info("Slack alert successfully delivered for logId={}", logEntity.getId()))
                    .doOnError(err -> log.error("Failed to send alert to Slack for logId={}: {}", logEntity.getId(), err.getMessage()))
                    .subscribe();

        } catch (Exception ex) {
            log.error("Unexpected error constructing or sending Slack alert for logId={}: {}", logEntity.getId(), ex.getMessage());
        }
    }

    private SlackPayload buildSlackPayload(
            Log logEntity,
            IncidentDiagnosis diagnosis,
            NotificationProperties.SlackProperties slackProps) {

        String severity = diagnosis != null && diagnosis.getSeverity() != null
                ? diagnosis.getSeverity()
                : (logEntity.getLevel() != null ? logEntity.getLevel().name() : "ERROR");

        String service = logEntity.getServiceName() != null ? logEntity.getServiceName() : "unknown-service";
        String environment = logEntity.getEnvironment() != null ? logEntity.getEnvironment().name() : "PRODUCTION";
        String anomalyScoreStr = logEntity.getDecisionScore() != null
                ? String.format("%.4f", logEntity.getDecisionScore())
                : "N/A";

        String summary = diagnosis != null && diagnosis.getSummary() != null
                ? diagnosis.getSummary()
                : logEntity.getMessage();

        String rootCause = diagnosis != null && diagnosis.getProbableRootCause() != null
                ? diagnosis.getProbableRootCause()
                : "Under investigation by AI assistant";

        String fix = diagnosis != null && diagnosis.getSuggestedFix() != null
                ? diagnosis.getSuggestedFix()
                : "Inspect service logs and check dependency health.";

        List<SlackPayload.Block> blocks = new ArrayList<>();

        // Header block
        blocks.add(SlackPayload.Block.builder()
                .type("header")
                .text(SlackPayload.TextObject.builder()
                        .type("plain_text")
                        .text("🚨 [" + severity + "] Incident Detected: " + service)
                        .emoji(true)
                        .build())
                .build());

        // Context fields
        List<SlackPayload.TextObject> fields = new ArrayList<>();
        fields.add(SlackPayload.TextObject.builder()
                .type("mrkdwn")
                .text("*Environment:*\n`" + environment + "`")
                .build());
        fields.add(SlackPayload.TextObject.builder()
                .type("mrkdwn")
                .text("*Severity:*\n*" + severity + "*")
                .build());
        fields.add(SlackPayload.TextObject.builder()
                .type("mrkdwn")
                .text("*Anomaly Detected:*\n" + (Boolean.TRUE.equals(logEntity.getAnomaly()) ? "⚠️ Yes (" + anomalyScoreStr + ")" : "ℹ️ No"))
                .build());
        fields.add(SlackPayload.TextObject.builder()
                .type("mrkdwn")
                .text("*Correlation ID:*\n`" + logEntity.getCorrelationId() + "`")
                .build());

        blocks.add(SlackPayload.Block.builder()
                .type("section")
                .fields(fields)
                .build());

        // Summary block
        blocks.add(SlackPayload.Block.builder()
                .type("section")
                .text(SlackPayload.TextObject.builder()
                        .type("mrkdwn")
                        .text("*Incident Summary:*\n" + summary)
                        .build())
                .build());

        // Root Cause block
        blocks.add(SlackPayload.Block.builder()
                .type("section")
                .text(SlackPayload.TextObject.builder()
                        .type("mrkdwn")
                        .text("*AI Root Cause Analysis:*\n" + rootCause)
                        .build())
                .build());

        // Remediation block
        StringBuilder fixText = new StringBuilder("*Recommended Remediation:*\n").append(fix);
        if (diagnosis != null && diagnosis.getRemediationCommands() != null && !diagnosis.getRemediationCommands().isEmpty()) {
            fixText.append("\n*Remediation Commands:*\n```\n");
            for (String cmd : diagnosis.getRemediationCommands()) {
                fixText.append(cmd).append("\n");
            }
            fixText.append("```");
        }

        blocks.add(SlackPayload.Block.builder()
                .type("section")
                .text(SlackPayload.TextObject.builder()
                        .type("mrkdwn")
                        .text(fixText.toString())
                        .build())
                .build());

        return SlackPayload.builder()
                .text("🚨 Incident Alert: [" + severity + "] " + service + " - " + summary)
                .channel(slackProps.getChannel())
                .username(slackProps.getUsername())
                .iconEmoji(slackProps.getIconEmoji())
                .blocks(blocks)
                .build();
    }
}

