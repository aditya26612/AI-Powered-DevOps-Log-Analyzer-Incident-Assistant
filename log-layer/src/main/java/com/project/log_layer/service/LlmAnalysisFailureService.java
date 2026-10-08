package com.project.log_layer.service;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LlmAnalysisFailureService {

    private final LogRepository logRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Log log) {
        log.setLlmAnalysisStatus(AnalysisStatus.FAILED);
        log.setLlmAnalyzedAt(LocalDateTime.now());

        logRepository.save(log);
    }
}