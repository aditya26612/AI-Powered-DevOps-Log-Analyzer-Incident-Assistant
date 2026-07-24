"""
===============================================================================
Batch Prediction Request Schema
===============================================================================
"""

from pydantic import BaseModel, Field

from app.api.v1.schemas.requests.prediction_request import PredictionRequest


class BatchPredictionRequest(BaseModel):
    """
    Request containing multiple logs.
    """

    logs: list[PredictionRequest] = Field(
        ...,
        min_length=1,
        description="Collection of logs for batch prediction.",
    )