from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():

    response = client.get(
        "/api/v1/system/health"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "UP"


def test_model_info():

    response = client.get(
        "/api/v1/system/model"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["model_name"] == "CuratedIsolationForest"
    assert body["model_version"] == "2.0.0-candidate"
    assert body["feature_count"] == 40
    assert body["calibrated_decision_threshold"] == -0.02
    assert body["requires_scaler"] is False


def test_model_info_alias():

    response = client.get(
        "/api/v1/system/model-info"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["model_name"] == "CuratedIsolationForest"
    assert body["model_version"] == "2.0.0-candidate"
    assert body["feature_count"] == 40
    assert body["calibrated_decision_threshold"] == -0.02
    assert body["requires_scaler"] is False