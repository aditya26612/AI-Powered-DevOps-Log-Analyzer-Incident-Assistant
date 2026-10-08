"""
===============================================================================
DevInsight AI - Matrix Builder
===============================================================================

Converts FeatureVector domain objects into NumPy matrices.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import numpy as np

from app.domain.feature_vector import FeatureVector


class MatrixBuilder:
    """
    Converts FeatureVectors into NumPy matrices.
    """

    @staticmethod
    def build(
        vectors: list[FeatureVector],
    ) -> np.ndarray:

        if not vectors:
            return np.empty((0, 0), dtype=float)

        return np.asarray(
            [vector.values for vector in vectors],
            dtype=float,
        )