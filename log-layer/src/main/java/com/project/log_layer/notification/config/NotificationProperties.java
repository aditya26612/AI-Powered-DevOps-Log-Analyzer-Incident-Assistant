package com.project.log_layer.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for incident notifications and alerting.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /**
     * Whether to automatically run LLM diagnosis when an anomaly or ERROR is detected during log ingestion.
     */
    private boolean autoDiagnoseAnomalies = true;

    /**
     * Slack notification settings.
     */
    private SlackProperties slack = new SlackProperties();

    /**
     * Email notification settings.
     */
    private EmailProperties email = new EmailProperties();

    @Data
    public static class SlackProperties {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String channel = "#devops-alerts";
        private String username = "DevInsight AI Incident Assistant";
        private String iconEmoji = ":rotating_light:";
    }

    @Data
    public static class EmailProperties {
        private boolean enabled = false;
        private String to = "";
        private String from = "alerts@devinsight.io";
        private String subjectPrefix = "[DevInsight Incident Alert] ";
    }
}

