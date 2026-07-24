"""
===============================================================================
DevInsight AI - Prediction Configuration
===============================================================================

Configuration used by the online inference pipeline.

This configuration is intentionally separate from TrainingConfig to keep
training and prediction concerns independent.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class PredictionConfig:
    """
    Configuration for the prediction pipeline.
    """

    artifact_directory: Path = Path("artifacts")

    enable_scaling: bool = True

    batch_size: int = 100