"""
===============================================================================
Batch Prediction Response Schema
===============================================================================
"""

from pydantic import BaseModel, Field

from app.api.v1.schemas.responses.prediction_response import PredictionResponse


class BatchPredictionResponse(BaseModel):
    """
    Response containing predictions for multiple logs.
    """

    predictions: list[PredictionResponse] = Field(
        ...,
        description="Prediction results for each log.",
    )