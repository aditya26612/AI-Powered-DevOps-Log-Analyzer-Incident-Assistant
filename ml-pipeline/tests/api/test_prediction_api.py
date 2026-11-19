import pytest
from fastapi import HTTPException

from app.api.v1 import dependencies
from app.core.config import Settings


def test_predict(prediction_client):

    response = prediction_client.post(
        "/api/v1/predict",
        json={
            "timestamp": "2026-07-20T18:00:00",
            "level": "ERROR",
            "service_name": "user-service",
            "message": "Database connection timeout",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert "prediction" in body
    assert "is_anomaly" in body
    assert "decision_score" in body


@pytest.mark.parametrize("configured_key, request_key, expected_status", [
    ("", "prediction-test-key", 503),
    ("   ", "prediction-test-key", 503),
    ("prediction-test-key", None, 401),
    ("prediction-test-key", "wrong-key", 401),
    ("prediction-test-key", "prediction-test-key", None),
])
def test_prediction_auth_configuration(monkeypatch, configured_key, request_key, expected_status):
    settings = Settings(INTERNAL_API_KEY=configured_key)
    monkeypatch.setattr(dependencies, "get_settings", lambda: settings)

    if expected_status is None:
        dependencies.verify_internal_api_key(request_key)
    else:
        with pytest.raises(HTTPException) as error:
            dependencies.verify_internal_api_key(request_key)
        assert error.value.status_code == expected_status
