"""
===============================================================================
DevInsight AI - Artifact Paths
===============================================================================
"""

from pathlib import Path
from typing import Final

ARTIFACT_DIRECTORY: Final = Path("artifacts")

MODEL_FILE: Final = ARTIFACT_DIRECTORY / "model.pkl"

SCALER_FILE: Final = ARTIFACT_DIRECTORY / "scaler.pkl"

METADATA_FILE: Final = ARTIFACT_DIRECTORY / "metadata.json"