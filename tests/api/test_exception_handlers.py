from fastapi import FastAPI
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.exceptions.custom_exceptions import (
    ModelNotLoadedException,
)
from app.exceptions.handlers import register_exception_handlers


def create_test_app() -> FastAPI:
    """
    Create an isolated FastAPI application
    for testing exception handlers.
    """

    app = FastAPI()

    register_exception_handlers(app)

    @app.get("/model-error")
    def model_error():
        raise ModelNotLoadedException()

    @app.get("/generic-error")
    def generic_error():
        raise Exception("Boom")

    @app.get("/http-error")
    def http_error():
        raise HTTPException(
            status_code=404,
            detail="Not Found",
        )

    @app.post("/validation-error")
    def validation_error(
        name: str,
    ):
        return {"name": name}

    return app

client = TestClient(
    create_test_app(),
    raise_server_exceptions=False,
)

def test_validation_exception():
    """
    Validation errors should use the standard response.
    """

    response = client.post(
        "/validation-error",
        json={},
    )

    assert response.status_code == 422

    body = response.json()

    assert body["status"] == 422
    assert body["error"] == "Unprocessable Entity"
    assert body["message"] == "Request validation failed."
    assert body["path"] == "/validation-error"
    assert "timestamp" in body


def test_http_exception():
    """
    HTTPException should use the standard response.
    """

    response = client.get("/http-error")

    assert response.status_code == 404

    body = response.json()

    assert body["status"] == 404
    assert body["error"] == "Not Found"
    assert body["message"] == "Not Found"
    assert body["path"] == "/http-error"
    assert "timestamp" in body


def test_model_not_loaded_exception():
    """
    Business exceptions should use the standard response.
    """

    response = client.get("/model-error")

    assert response.status_code == 503

    body = response.json()

    assert body["status"] == 503
    assert body["error"] == "Service Unavailable"
    assert body["message"] == "Prediction model is unavailable."
    assert body["path"] == "/model-error"
    assert "timestamp" in body


def test_generic_exception():
    """
    Unexpected exceptions should use the standard response.
    """

    response = client.get("/generic-error")

    assert response.status_code == 500

    body = response.json()

    assert body["status"] == 500
    assert body["error"] == "Internal Server Error"
    assert body["message"] == "An unexpected error occurred."
    assert body["path"] == "/generic-error"
    assert "timestamp" in body