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
        description="Raw prediction returned by the ML model (-1 = Anomaly, 1 = Normal).",
    )

    prediction_label: str = Field(
        ...,
        description="Human-readable prediction label.",
    )

    is_anomaly: bool = Field(
        ...,
        description="True if the log is classified as anomalous.",
    )

    decision_score: float = Field(
        ...,
        description="Isolation Forest anomaly score.",
    )

    model_version: str = Field(
        ...,
        description="Version of the ML model used for inference.",
    )