"""
===============================================================================
DevInsight AI - Feature Orchestrator
===============================================================================

Coordinates all feature extractors and produces a validated FeatureVector.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.domain.feature_vector import FeatureVector
from app.domain.log_entry import LogEntry
from app.features.extractors.metadata_extractor import MetadataExtractor
from app.features.extractors.regex_extractor import RegexExtractor
from app.features.extractors.statistical_extractor import (
    StatisticalExtractor,
)
from app.features.extractors.text_extractor import TextExtractor
from app.features.feature_names import FEATURE_NAMES
from app.features.validators.feature_validator import FeatureValidator


class FeatureOrchestrator:
    """
    Coordinates all feature extractors.
    """

    def __init__(self) -> None:

        self._extractors = (
            MetadataExtractor(),
            TextExtractor(),
            RegexExtractor(),
            StatisticalExtractor(),
        )

    def extract(
        self,
        log: LogEntry,
    ) -> FeatureVector:
        """
        Generate the complete feature vector for a log entry.
        """

        features: dict[str, float | int | bool] = {}

        # Run every extractor
        for extractor in self._extractors:
            features.update(extractor.extract(log))

        # Validate feature set
        FeatureValidator.validate(features)

        # Convert to ordered numeric vector
        values = []

        for feature_name in FEATURE_NAMES:

            value = features[feature_name]

            if isinstance(value, bool):
                values.append(float(value))
            else:
                values.append(float(value))

        return FeatureVector(
            values=values,
            feature_names=FEATURE_NAMES.copy(),
        )