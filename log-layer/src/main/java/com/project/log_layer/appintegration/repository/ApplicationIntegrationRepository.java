package com.project.log_layer.appintegration.repository;

import com.project.log_layer.appintegration.entity.ApplicationIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationIntegrationRepository extends JpaRepository<ApplicationIntegration, Long> {

    Optional<ApplicationIntegration> findByApplicationName(String applicationName);

    Optional<ApplicationIntegration> findByApplicationNameAndUserEmail(String applicationName, String userEmail);

    Optional<ApplicationIntegration> findByIdAndUserEmail(Long id, String userEmail);

    Optional<ApplicationIntegration> findByApiKey(String apiKey);

    List<ApplicationIntegration> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);

    boolean existsByApplicationName(String applicationName);

    boolean existsByApplicationNameAndUserEmail(String applicationName, String userEmail);
}

