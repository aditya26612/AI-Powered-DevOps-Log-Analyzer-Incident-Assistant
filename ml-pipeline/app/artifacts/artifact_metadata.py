"""
===============================================================================
DevInsight AI - Artifact Metadata
===============================================================================
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime
import platform

import sklearn

from app.features.feature_names import (
    FEATURE_COUNT,
    FEATURE_NAMES,
)
from app.models.model_config import (
    FEATURE_VERSION,
    MODEL_NAME,
    MODEL_VERSION,
)


@dataclass(slots=True)
class ModelMetadata:

    model_name: str

    model_version: str

    feature_version: str

    feature_count: int

    feature_names: list[str]

    python_version: str

    sklearn_version: str

    trained_at: str

    @classmethod
    def create(cls):

        return cls(
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
            feature_version=FEATURE_VERSION,
            feature_count=FEATURE_COUNT,
            feature_names=FEATURE_NAMES.copy(),
            python_version=platform.python_version(),
            sklearn_version=sklearn.__version__,
            trained_at=datetime.utcnow().isoformat(),
        )

    def to_dict(self):

        return asdict(self)