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