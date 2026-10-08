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

        return MatrixBuilder.build(vectors)