package com.project.log_layer.enums;

/**
 * Represents the deployment environment from which
 * a log entry is generated.
 *
 * Used for filtering logs and environment-specific analysis.
 */
public enum Environment {

    DEVELOPMENT,
    TESTING,
    QA,
    STAGING,
    PRODUCTION

}