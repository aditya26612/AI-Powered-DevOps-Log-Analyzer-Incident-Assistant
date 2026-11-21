"""
===============================================================================
API Dependencies
===============================================================================
"""

from functools import lru_cache
from hmac import compare_digest
from typing import Optional

from fastapi import Header, HTTPException, status

from app.bootstrap.prediction import PredictionBootstrap
from app.core.config import get_settings
from app.services.prediction_service import PredictionService


@lru_cache(maxsize=1)
def get_prediction_service() -> PredictionService:
    """
    Returns a singleton PredictionService instance.

    The model artifacts are loaded only once and reused for
    all incoming requests.
    """
    return PredictionBootstrap.create()


def verify_internal_api_key(
    x_api_key: Optional[str] = Header(None, alias="X-API-Key"),
) -> None:
    """
    Validates inter-service requests using the shared internal API key.
    Rejects unauthorized callers attempting to invoke the ML model directly.
    """
    internal_api_key = get_settings().INTERNAL_API_KEY
    if not internal_api_key.strip():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="ML prediction authentication is not configured",
        )

    if not x_api_key or not compare_digest(x_api_key.encode("utf-8"), internal_api_key.encode("utf-8")):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized: Invalid or missing X-API-Key header",
        )
