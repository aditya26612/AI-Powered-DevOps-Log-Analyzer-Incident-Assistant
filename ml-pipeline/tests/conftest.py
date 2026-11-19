import sys
from pathlib import Path

import pytest

# Ensure ml-pipeline directory is on sys.path for test runners executing from any directory
ml_pipeline_root = Path(__file__).resolve().parent.parent
if str(ml_pipeline_root) not in sys.path:
    sys.path.insert(0, str(ml_pipeline_root))


@pytest.fixture
def prediction_client(monkeypatch):
    from fastapi.testclient import TestClient

    from app.api.v1 import dependencies
    from app.core.config import Settings
    from app.main import app

    settings = Settings(INTERNAL_API_KEY="prediction-test-key")
    monkeypatch.setattr(dependencies, "get_settings", lambda: settings)
    client = TestClient(app, headers={"X-API-Key": settings.INTERNAL_API_KEY})
    yield client
    client.close()
