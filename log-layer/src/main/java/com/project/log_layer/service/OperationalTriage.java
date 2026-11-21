package com.project.log_layer.service;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.LogLevel;

/** Local policy for log-level triage. Diagnosis execution has a separate lifecycle. */
public final class OperationalTriage {
    private OperationalTriage() {}

    public static String ruleId(Log log) {
        return log.getLevel() == LogLevel.ERROR || log.getLevel() == LogLevel.FATAL
                ? "EXPLICIT_CRITICAL_SEVERITY_V1" : null;
    }

    public static Boolean statisticalAnomaly(Log log) {
        return log.getAnalysisStatus() == AnalysisStatus.COMPLETED ? log.getAnomaly() : null;
    }

    public static boolean eligible(Log log) {
        return ruleId(log) != null || Boolean.TRUE.equals(statisticalAnomaly(log));
    }
}
