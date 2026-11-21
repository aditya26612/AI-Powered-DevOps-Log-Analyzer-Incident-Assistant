package com.project.log_layer.service;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import com.project.log_layer.tenant.TenantSqlScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LlmAnalysisFailureService {

    private final LogRepository logRepository;
    private final TenantSqlScope tenantScope;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailedById(long id, UUID organization) {
        if (organization != null) {
            tenantScope.enter(organization);
        }
        logRepository.findById(id).ifPresent(this::markAsFailed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Log log) {
        if (log.getOrganizationId() != null) {
            tenantScope.enter(log.getOrganizationId());
        }
        log.setLlmAnalysisStatus(AnalysisStatus.FAILED);
        log.setLlmAnalyzedAt(LocalDateTime.now());

        logRepository.save(log);
    }
}