"""
===============================================================================
DevInsight AI - Prediction Service Tests
===============================================================================
"""

from __future__ import annotations

from datetime import datetime

from app.bootstrap.prediction import PredictionBootstrap
from app.domain.log_entry import LogEntry
from app.domain.prediction_result import PredictionResult


def test_prediction_service():

    # Arrange
    service = PredictionBootstrap.create()

    log = LogEntry(
        timestamp=datetime.now(),
        level="ERROR",
        service_name="user-service",
        message="java.lang.NullPointerException occurred while processing request",
    )

    # Act
    result = service.predict(log)

    # Assert
    assert isinstance(result, PredictionResult)

    assert isinstance(result.prediction, int)

    assert isinstance(result.is_anomaly, bool)

    assert isinstance(result.decision_score, float)

    assert result.prediction in (-1, 1)