"""
===============================================================================
DevInsight AI - Curated Feature Orchestrator (V2 Feature Pipeline)
===============================================================================
Engineered to resolve:
1. Zero-variance features (day_of_week, starts_with_timestamp, empty_message, contains_uuid, line_count)
2. Collinear duplicates (numeric_density, average_line_length)
3. Destructive service hash modulo splits (replaced with categorical service flags)
4. Optional severity decoupling (ability to train completely severity-agnostic models)
===============================================================================
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Final
from app.domain.feature_vector import FeatureVector
from app.domain.log_entry import LogEntry
from app.features.extractors.metadata_extractor import MetadataExtractor
from app.features.extractors.regex_extractor import RegexExtractor
from app.features.extractors.statistical_extractor import StatisticalExtractor
from app.features.extractors.text_extractor import TextExtractor
from app.features.constants import LOG_LEVEL_ENCODING, UNKNOWN_LOG_LEVEL


KNOWN_SERVICES: Final[list[str]] = [
    "api-gateway",
    "user-service",
    "payment-service",
    "order-service",
    "k8s-kubelet",
    "docker-daemon",
]

CURATED_CORE_FEATURE_NAMES: Final[list[str]] = [
    # Clean text statistics (13 features)
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
    # Clean statistical & information theory (4 features - dropped collinear duplicates & zero-var)
    "entropy",
    "unique_token_ratio",
    "character_diversity",
    "punctuation_ratio",
    # Operational regex keywords (14 features - dropped zero-var contains_uuid)
    "contains_exception",
    "contains_stacktrace",
    "contains_timeout",
    "contains_database_error",
    "contains_network_error",
    "contains_memory_error",
    "contains_http_status",
    "contains_ip_address",
    "contains_port_number",
    "contains_kubernetes_keyword",
    "contains_docker_keyword",
    "contains_spring_keyword",
    "contains_jvm_keyword",
    "contains_authentication_keyword",
    # Temporal (1 feature - dropped zero-var day_of_week)
    "hour_of_day",
    # Categorical service one-hot representation (7 features - replaces arbitrary hash modulo)
    "is_service_api_gateway",
    "is_service_user_service",
    "is_service_payment_service",
    "is_service_order_service",
    "is_service_k8s_kubelet",
    "is_service_docker_daemon",
    "is_service_unknown",
]


class CuratedFeatureOrchestrator:
    """Extracts pruned, high-signal log features without zero-variance noise."""

    def __init__(self, include_severity: bool = True) -> None:
        self.include_severity = include_severity
        self._text_extractor = TextExtractor()
        self._regex_extractor = RegexExtractor()
        self._stat_extractor = StatisticalExtractor()

        if include_severity:
            self.feature_names = ["log_level"] + CURATED_CORE_FEATURE_NAMES
        else:
            self.feature_names = list(CURATED_CORE_FEATURE_NAMES)

    def extract(self, log: LogEntry) -> FeatureVector:
        # Defensive fallback for null or malformed log fields
        safe_msg = "" if log.message is None else str(log.message)
        safe_level = "" if log.level is None else str(log.level)
        safe_service = "" if log.service_name is None else str(log.service_name)
        safe_time = datetime.now(timezone.utc) if log.timestamp is None else log.timestamp
        sanitized_log = LogEntry(
            timestamp=safe_time,
            level=safe_level,
            service_name=safe_service,
            message=safe_msg,
            application=log.application,
            environment=log.environment,
            thread=log.thread,
            logger=log.logger,
            exception=log.exception,
            correlation_id=log.correlation_id,
            is_anomaly=log.is_anomaly,
            label=log.label,
            label_rationale=log.label_rationale,
            log_id=log.log_id,
        )

        raw_text = self._text_extractor.extract(sanitized_log)
        raw_regex = self._regex_extractor.extract(sanitized_log)
        raw_stat = self._stat_extractor.extract(sanitized_log)

        values: list[float] = []

        if self.include_severity:
            level_code = LOG_LEVEL_ENCODING.get(sanitized_log.level.upper(), UNKNOWN_LOG_LEVEL)
            values.append(float(level_code))

        # Text features
        values.append(float(raw_text["message_length"]))
        values.append(float(raw_text["token_count"]))
        values.append(float(raw_text["average_word_length"]))
        values.append(float(raw_text["uppercase_ratio"]))
        values.append(float(raw_text["lowercase_ratio"]))
        values.append(float(raw_text["digit_ratio"]))
        values.append(float(raw_text["alphabetic_ratio"]))
        values.append(float(raw_text["special_character_ratio"]))
        values.append(float(raw_text["longest_token_length"]))
        values.append(float(raw_text["shortest_token_length"]))
        values.append(float(raw_text["average_token_length_variance"]))
        values.append(float(raw_text["whitespace_ratio"]))
        values.append(float(bool(raw_text["contains_mixed_case"])))

        # Statistical features (excluding duplicates)
        values.append(float(raw_stat["entropy"]))
        values.append(float(raw_stat["unique_token_ratio"]))
        values.append(float(raw_stat["character_diversity"]))
        values.append(float(raw_stat["punctuation_ratio"]))

        # Regex features (excluding contains_uuid)
        values.append(float(bool(raw_regex["contains_exception"])))
        values.append(float(bool(raw_regex["contains_stacktrace"])))
        values.append(float(bool(raw_regex["contains_timeout"])))
        values.append(float(bool(raw_regex["contains_database_error"])))
        values.append(float(bool(raw_regex["contains_network_error"])))
        values.append(float(bool(raw_regex["contains_memory_error"])))
        values.append(float(bool(raw_regex["contains_http_status"])))
        values.append(float(bool(raw_regex["contains_ip_address"])))
        values.append(float(bool(raw_regex["contains_port_number"])))
        values.append(float(bool(raw_regex["contains_kubernetes_keyword"])))
        values.append(float(bool(raw_regex["contains_docker_keyword"])))
        values.append(float(bool(raw_regex["contains_spring_keyword"])))
        values.append(float(bool(raw_regex["contains_jvm_keyword"])))
        values.append(float(bool(raw_regex["contains_authentication_keyword"])))

        # Temporal
        values.append(float(sanitized_log.timestamp.hour))

        # Categorical service one-hot
        srv = (sanitized_log.service_name or "").lower().strip()
        is_known = False
        for known in KNOWN_SERVICES:
            match = float(srv == known)
            values.append(match)
            if match == 1.0:
                is_known = True

        values.append(0.0 if is_known else 1.0)

        return FeatureVector(
            values=values,
            feature_names=list(self.feature_names),
        )
