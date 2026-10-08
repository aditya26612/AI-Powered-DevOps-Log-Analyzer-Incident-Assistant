"""
===============================================================================
DevInsight AI - Statistical Feature Extractor
===============================================================================

Extracts statistical features from log messages.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.domain.log_entry import LogEntry
from app.features.contracts.extractor import FeatureExtractor
from app.features.utils.entropy import normalized_entropy
from app.features.utils.text_utils import (
    average_line_length,
    character_diversity,
    line_count,
    numeric_density,
    punctuation_ratio,
    unique_token_ratio,
)


class StatisticalExtractor(FeatureExtractor):
    """
    Extract statistical features.
    """

    def extract(
        self,
        log: LogEntry,
    ) -> dict[str, float | int]:

        message = log.message

        return {

            "entropy": normalized_entropy(message),

            "unique_token_ratio": unique_token_ratio(message),

            "character_diversity": character_diversity(message),

            "punctuation_ratio": punctuation_ratio(message),

            "numeric_density": numeric_density(message),

            "average_line_length": average_line_length(message),

            "line_count": line_count(message),

        }