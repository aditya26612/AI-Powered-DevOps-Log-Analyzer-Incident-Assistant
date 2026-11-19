def test_batch_prediction(prediction_client):

    response = prediction_client.post(
        "/api/v1/predict/batch",
        json={
            "logs": [
                {
                    "timestamp": "2026-07-20T18:00:00",
                    "level": "INFO",
                    "service_name": "user-service",
                    "message": "Application started",
                },
                {
                    "timestamp": "2026-07-20T18:05:00",
                    "level": "ERROR",
                    "service_name": "payment-service",
                    "message": "Database timeout",
                },
            ]
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert "predictions" in body

    assert len(body["predictions"]) == 2
