"""
===============================================================================
DevInsight AI - Regex Feature Extractor
===============================================================================

Extracts boolean features using compiled regular expressions.

All regex definitions are centralized in patterns.py.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.domain.log_entry import LogEntry
from app.features.contracts.extractor import FeatureExtractor
from app.features.patterns import REGEX_FEATURE_MAPPING


class RegexExtractor(FeatureExtractor):
    """
    Extract regex-based features from a log message.
    """

    def extract(
        self,
        log: LogEntry,
    ) -> dict[str, bool]:

        message = log.message

        return {
            feature_name: bool(pattern.search(message))
            for feature_name, pattern in REGEX_FEATURE_MAPPING.items()
        }