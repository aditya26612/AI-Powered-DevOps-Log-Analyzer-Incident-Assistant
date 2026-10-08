"""
===============================================================================
DevInsight AI - Anomaly Prediction Domain Model
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True, frozen=True)
class AnomalyPrediction:
    """
    Represents the output of the ML model.
    """

    is_anomaly: bool

    anomaly_score: float

    confidence: float | None = None

    explanation: str | None = None