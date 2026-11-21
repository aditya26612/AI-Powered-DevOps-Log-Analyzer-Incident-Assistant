"""
===============================================================================
Prediction Response Schema
===============================================================================
"""

from typing import Literal
from pydantic import BaseModel, Field, FiniteFloat


class PredictionResponse(BaseModel):
    """
    Response returned after anomaly prediction.
    """

    prediction: Literal[-1, 1] = Field(
        ...,
        description="Raw prediction returned by the ML model (-1 = Anomaly, 1 = Normal).",
    )

    prediction_label: Literal["Anomaly", "Normal"] = Field(
        ...,
        description="Human-readable prediction label.",
    )

    is_anomaly: bool = Field(
        ...,
        description="True if the log is classified as anomalous.",
    )

    decision_score: FiniteFloat = Field(
        ...,
        description="Isolation Forest anomaly score.",
    )

    model_version: str = Field(
        ...,
        description="Version of the ML model used for inference.",
    )
