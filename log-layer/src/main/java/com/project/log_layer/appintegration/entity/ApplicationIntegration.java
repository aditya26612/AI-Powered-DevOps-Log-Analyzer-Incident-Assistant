package com.project.log_layer.appintegration.entity;

import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.TechStack;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an enterprise application integration registered with DevInsight.
 * Manages service metadata, tech stack specification, dedicated API keys,
 * and service-specific Slack alert routing channels.
 */
@Entity
@Table(name = "application_integrations", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "application_name"}), indexes = {
        @Index(name = "idx_integration_app_name", columnList = "application_name"),
        @Index(name = "idx_integration_api_key", columnList = "api_key"),
        @Index(name = "idx_integration_user_email", columnList = "user_email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "deactivated_at")
    private java.time.OffsetDateTime deactivatedAt;

    @Column(name = "slack_webhook_ciphertext")
    private String slackWebhookCiphertext;

    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tech_stack", nullable = false, length = 50)
    private TechStack techStack;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 30)
    private Environment environment;

    @Column(name = "api_key", length = 120)
    private String apiKey;

    @Column(name = "slack_webhook_url", length = 500)
    private String slackWebhookUrl;

    @Column(name = "slack_channel_name", length = 100)
    private String slackChannelName;

    @Column(name = "alert_on_anomaly", nullable = false)
    @Builder.Default
    private Boolean alertOnAnomaly = true;

    @Column(name = "alert_on_error", nullable = false)
    @Builder.Default
    private Boolean alertOnError = true;

    @Column(name = "anomaly_threshold")
    @Builder.Default
    private Double anomalyThreshold = -0.1;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

