"""
===============================================================================
API Dependencies
===============================================================================
"""

from functools import lru_cache

from app.bootstrap.prediction import PredictionBootstrap
from app.services.prediction_service import PredictionService


@lru_cache(maxsize=1)
def get_prediction_service() -> PredictionService:
    """
    Returns a singleton PredictionService instance.

    The model artifacts are loaded only once and reused for
    all incoming requests.
    """

    return PredictionBootstrap.create()