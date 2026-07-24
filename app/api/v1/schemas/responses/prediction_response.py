"""
===============================================================================
Prediction Response Schema
===============================================================================
"""

from pydantic import BaseModel, Field


class PredictionResponse(BaseModel):
    """
    Response returned after anomaly prediction.
    """

    prediction: int = Field(
        ...,
        description="Raw prediction returned by the ML model (1 or -1).",
    )

    is_anomaly: bool = Field(
        ...,
        description="True if the log is classified as anomalous.",
    )

    decision_score: float = Field(
        ...,
        description="Isolation Forest anomaly score.",
    )