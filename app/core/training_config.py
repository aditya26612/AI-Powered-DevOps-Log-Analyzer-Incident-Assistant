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
    # Pipeline
    # =========================================================================

    enable_scaling: bool = True

    save_artifacts: bool = True

    # =========================================================================
    # Training
    # =========================================================================

    contamination: float = 0.05

    random_state: int = 42