from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_predict():

    response = client.post(
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