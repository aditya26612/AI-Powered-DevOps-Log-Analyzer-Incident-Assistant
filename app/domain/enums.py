"""
===============================================================================
DevInsight AI - Shared Enumerations
===============================================================================
"""

from enum import Enum


class PredictionLabel(str, Enum):
    """
    Prediction labels.
    """

    NORMAL = "NORMAL"

    ANOMALY = "ANOMALY"


class Environment(str, Enum):
    """
    Supported deployment environments.
    """

    DEVELOPMENT = "development"

    TEST = "test"

    STAGING = "staging"

    PRODUCTION = "production"