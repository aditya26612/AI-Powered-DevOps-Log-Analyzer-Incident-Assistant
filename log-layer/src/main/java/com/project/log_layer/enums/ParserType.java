package com.project.log_layer.enums;

/**
 * Represents the supported parser implementations
 * available in the Log Layer.
 *
 * Used by the ParserFactory to select the
 * appropriate parser for a log source.
 */
public enum ParserType {

    SPRING_BOOT,
    DOCKER,
    NGINX,
    KUBERNETES,
    APACHE,
    JENKINS,
    KAFKA,
    REDIS,
    MYSQL,
    SYSTEM,
    CUSTOM

}