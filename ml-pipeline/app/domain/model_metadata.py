"""
===============================================================================
DevInsight AI - Model Metadata
===============================================================================

Represents metadata describing a trained machine learning model.

This metadata is persisted alongside the trained model artifacts and is used
for operational endpoints, diagnostics, and model lifecycle management.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass(slots=True, frozen=True)
class ModelMetadata:
    """
    Metadata describing a trained machine learning model.
    """

    model_name: str

    model_version: str

    algorithm: str

    feature_version: str

    feature_count: int

    feature_names: list[str]

    dataset_size: int

    contamination: float

    random_state: int

    python_version: str

    sklearn_version: str

    trained_at: datetime

    max_samples: int | None = None

    max_features: float | None = None

    requires_scaler: bool = True

    calibrated_decision_threshold: float = 0.0

    status: str | None = None

    sha256_model: str | None = None
