package com.project.log_layer.appintegration.repository;

import com.project.log_layer.appintegration.entity.ApplicationIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationIntegrationRepository extends JpaRepository<ApplicationIntegration, Long> {

    Optional<ApplicationIntegration> findByApplicationName(String applicationName);

    Optional<ApplicationIntegration> findByApplicationNameAndUserEmail(String applicationName, String userEmail);

    Optional<ApplicationIntegration> findByIdAndUserEmail(Long id, String userEmail);

    @Query("""
            select a.slackWebhookCiphertext as ciphertext, a.slackChannelName as channel,
                   a.alertOnAnomaly as alertOnAnomaly, a.alertOnError as alertOnError
            from ApplicationIntegration a
            where a.id = :id and a.organizationId = :organizationId and a.deactivatedAt is null
            """)
    Optional<SlackRoute> findSlackRoute(Long id, UUID organizationId);

    interface SlackRoute {
        String getCiphertext();
        String getChannel();
        Boolean getAlertOnAnomaly();
        Boolean getAlertOnError();
    }

    Optional<ApplicationIntegration> findByApiKey(String apiKey);

    List<ApplicationIntegration> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);

    boolean existsByApplicationName(String applicationName);

    boolean existsByApplicationNameAndUserEmail(String applicationName, String userEmail);
}

