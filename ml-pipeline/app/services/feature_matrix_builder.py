"""
===============================================================================
DevInsight AI - Feature Matrix Builder
===============================================================================

Converts LogEntry domain objects into a NumPy feature matrix.

Responsibilities
----------------
- Extract features from logs
- Convert FeatureVectors into a NumPy matrix

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from numbers import Real

import numpy as np

from app.domain.log_entry import LogEntry
from app.features.orchestrator import FeatureOrchestrator
from app.services.matrix_builder import MatrixBuilder


class FeatureMatrixBuilder:
    """
    Builds a NumPy feature matrix from LogEntry objects.
    """

    def __init__(
        self,
        orchestrator: FeatureOrchestrator,
    ) -> None:

        self._orchestrator = orchestrator

    def build(
        self,
        logs: list[LogEntry],
    ) -> np.ndarray:

        vectors = [
            self._orchestrator.extract(log)
            for log in logs
        ]

        if any(vector.feature_names != self._orchestrator.feature_names
               or len(vector.values) != len(vector.feature_names) for vector in vectors):
            raise ValueError("Feature schema mismatch")
        if any(not isinstance(value, Real) for vector in vectors for value in vector.values):
            raise ValueError("Invalid feature values")
        matrix = MatrixBuilder.build(vectors)
        # A variance computed over even a 100,000-character message is bounded by 10**10.
        if not np.isfinite(matrix).all() or (np.abs(matrix) > 10**10).any():
            raise ValueError("Invalid feature values")
        return matrix
