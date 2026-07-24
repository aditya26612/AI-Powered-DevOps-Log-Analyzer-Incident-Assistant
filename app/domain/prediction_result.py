"""
===============================================================================
DevInsight AI - Prediction Result
===============================================================================

Represents the outcome of a single inference performed by the
PredictionService.

This is a domain object and must remain independent of FastAPI,
Pydantic, or any transport layer.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class PredictionResult:
    """
    Result of a single prediction.
    """

    prediction: int
    is_anomaly: bool
    decision_score: float