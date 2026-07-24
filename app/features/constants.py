"""
===============================================================================
DevInsight AI - Feature Engineering Constants
===============================================================================

This module contains all shared constants used throughout the Feature
Engineering pipeline.

No magic numbers or hardcoded values should exist elsewhere in the project.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from typing import Final

# =============================================================================
# Application
# =============================================================================

FEATURE_SCHEMA_VERSION: Final[str] = "1.0.0"

# =============================================================================
# Log Level Encoding
# =============================================================================

LOG_LEVEL_ENCODING: Final[dict[str, int]] = {
    "TRACE": 0,
    "DEBUG": 1,
    "INFO": 2,
    "WARN": 3,
    "ERROR": 4,
    "FATAL": 5,
}

UNKNOWN_LOG_LEVEL: Final[int] = -1

# =============================================================================
# Service Encoding
# =============================================================================

# Number of hash buckets used for service name hashing.
# Using hashing instead of manual encoding allows unseen services
# to be handled during inference.

SERVICE_HASH_BUCKETS: Final[int] = 256

# =============================================================================
# Feature Defaults
# =============================================================================

DEFAULT_NUMERIC_VALUE: Final[float] = 0.0

DEFAULT_BOOLEAN_VALUE: Final[int] = 0

DEFAULT_STRING_VALUE: Final[str] = ""

# =============================================================================
# Text Processing
# =============================================================================

MAX_MESSAGE_LENGTH: Final[int] = 100_000

MIN_MESSAGE_LENGTH: Final[int] = 0

TOKEN_SEPARATOR_REGEX: Final[str] = r"\s+"

SPECIAL_CHARACTERS: Final[str] = (
    "{}[]()<>=,:;\"'`|\\/@#$%^&*-+!?._"
)

# =============================================================================
# Statistical Processing
# =============================================================================

EPSILON: Final[float] = 1e-9

# =============================================================================
# Time Features
# =============================================================================

HOURS_PER_DAY: Final[int] = 24

DAYS_PER_WEEK: Final[int] = 7

# =============================================================================
# Prediction Labels
# =============================================================================

PREDICTION_NORMAL: Final[str] = "NORMAL"

PREDICTION_ANOMALY: Final[str] = "ANOMALY"

# =============================================================================
# Boolean Feature Values
# =============================================================================

TRUE_VALUE: Final[int] = 1

FALSE_VALUE: Final[int] = 0

# =============================================================================
# Feature Categories
# =============================================================================

METADATA_FEATURE_GROUP: Final[str] = "metadata"

TEXT_FEATURE_GROUP: Final[str] = "text"

REGEX_FEATURE_GROUP: Final[str] = "regex"

STATISTICAL_FEATURE_GROUP: Final[str] = "statistical"

# =============================================================================
# Supported Log Levels
# =============================================================================

SUPPORTED_LOG_LEVELS: Final[set[str]] = {
    "TRACE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
    "FATAL",
}

# =============================================================================
# Supported Services (Current Dataset)
# =============================================================================

SUPPORTED_SERVICES: Final[set[str]] = {
    "user-service",
    "payment-service",
    "api-gateway",
    "k8s-kubelet",
    "docker-daemon",
}

# =============================================================================
# Regex Feature Names
# =============================================================================

REGEX_FEATURES: Final[tuple[str, ...]] = (
    "contains_exception",
    "contains_stacktrace",
    "contains_timeout",
    "contains_database_error",
    "contains_network_error",
    "contains_memory_error",
    "contains_http_status",
    "contains_ip_address",
    "contains_uuid",
    "contains_port_number",
    "contains_kubernetes_keyword",
    "contains_docker_keyword",
    "contains_spring_keyword",
    "contains_jvm_keyword",
    "contains_authentication_keyword",
)

# =============================================================================
# Text Feature Names
# =============================================================================

TEXT_FEATURES: Final[tuple[str, ...]] = (
    "message_length",
    "token_count",
    "average_word_length",
    "uppercase_ratio",
    "digit_ratio",
    "special_character_ratio",
)

# =============================================================================
# Statistical Feature Names
# =============================================================================

STATISTICAL_FEATURES: Final[tuple[str, ...]] = (
    "entropy",
    "unique_token_ratio",
    "character_diversity",
    "punctuation_ratio",
    "whitespace_ratio",
)

# =============================================================================
# Metadata Feature Names
# =============================================================================

METADATA_FEATURES: Final[tuple[str, ...]] = (
    "log_level",
    "service_hash",
    "hour_of_day",
    "day_of_week",
)

# =============================================================================
# Miscellaneous
# =============================================================================

EMPTY_TEXT: Final[str] = ""

UNKNOWN_SERVICE: Final[str] = "unknown"

UNKNOWN_FEATURE_VALUE: Final[int] = -1