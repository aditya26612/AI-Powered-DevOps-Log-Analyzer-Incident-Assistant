"""
===============================================================================
DevInsight AI - Training Configuration
===============================================================================

Configuration for the training pipeline.

This module contains only pipeline-level configuration.

Model hyperparameters belong in ModelConfig.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


DEFAULT_TRAINING_OUTPUT_DIR = Path("artifacts/training-runs")


@dataclass(slots=True, frozen=True)
class TrainingConfig:
    """
    Configuration for the training pipeline.
    """

    # =========================================================================
    # Dataset
    # =========================================================================

    dataset_path: Path = Path("data/synthetic_logs.json")

    # =========================================================================
    # Pipeline & Partitions
    # =========================================================================

    enable_scaling: bool = True

    save_artifacts: bool = True

    test_size: float = 0.20

    validation_size: float = 0.10

    split_strategy: str = "template_grouped"

    feature_set: str = "curated"

    # =========================================================================
    # Model Hyperparameters
    # =========================================================================

    contamination: float = 0.05

    random_state: int = 42

    n_estimators: int = 200

    max_samples: int | float | str = 256

    max_features: float = 1.0

    bootstrap: bool = False

    decision_threshold: float = 0.0

    # =========================================================================
    # Output Artifacts
    # =========================================================================

    model_output_dir: Path = DEFAULT_TRAINING_OUTPUT_DIR

    artifacts_version: str = "2.0.0"

    model_name: str = "CuratedIsolationForest"
