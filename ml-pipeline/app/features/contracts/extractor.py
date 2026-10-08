"""
===============================================================================
DevInsight AI - Feature Extractor Contract
===============================================================================

Defines the abstract interface for all feature extractors.

Every feature extractor must implement the extract() method and return
a dictionary of feature names mapped to extracted values.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.domain.log_entry import LogEntry


class FeatureExtractor(ABC):
    """
    Base interface for all feature extractors.
    """

    @abstractmethod
    def extract(
        self,
        log: LogEntry,
    ) -> dict[str, float | bool | int]:
        """
        Extract features from a log entry.

        Parameters
        ----------
        log : LogEntry
            Domain log object.

        Returns
        -------
        dict[str, float | bool | int]
            Mapping of feature names to extracted values.
        """
        raise NotImplementedError