"""
===============================================================================
DevInsight AI - Canonical Feature Names
===============================================================================

Defines the canonical feature order used across the entire ML pipeline.

IMPORTANT:
The order of these features MUST NEVER change once a model has been trained.

This file is the single source of truth for:

- Feature Extraction
- Model Training
- Model Inference
- Feature Validation
- Metadata Verification

Changing the order without retraining the model will produce invalid
predictions.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from typing import Final

# =============================================================================
# Metadata Features
# =============================================================================

METADATA_FEATURE_NAMES: Final[list[str]] = [
    "log_level",
    "service_hash",
    "hour_of_day",
    "day_of_week",
]

# =============================================================================
# Text Features
# =============================================================================

TEXT_FEATURE_NAMES: Final[list[str]] = [
    "message_length",
    "token_count",
    "average_word_length",
    "uppercase_ratio",
    "lowercase_ratio",
    "digit_ratio",
    "alphabetic_ratio",
    "special_character_ratio",
    "longest_token_length",
    "shortest_token_length",
    "average_token_length_variance",
    "whitespace_ratio",
    "contains_mixed_case",
    "starts_with_timestamp",
    "empty_message",
]

# =============================================================================
# Statistical Features
# =============================================================================

STATISTICAL_FEATURE_NAMES = [
    "entropy",
    "unique_token_ratio",
    "character_diversity",
    "punctuation_ratio",
    "numeric_density",
    "average_line_length",
    "line_count",
]

# =============================================================================
# Regex Features
# =============================================================================

REGEX_FEATURE_NAMES: Final[list[str]] = [
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
]

# =============================================================================
# Complete Canonical Feature List
# =============================================================================

FEATURE_NAMES: Final[list[str]] = (
    METADATA_FEATURE_NAMES
    + TEXT_FEATURE_NAMES
    + REGEX_FEATURE_NAMES
    + STATISTICAL_FEATURE_NAMES
)

# =============================================================================
# Feature Count
# =============================================================================

FEATURE_COUNT: Final[int] = len(FEATURE_NAMES)