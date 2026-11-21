import sys
import os
from pathlib import Path

# Compatibility shim: modern Starlette versions removed on_startup/on_shutdown from Router
try:
    import starlette.routing
    _orig_router_init = starlette.routing.Router.__init__

    def _patched_router_init(self, *args, **kwargs):
        kwargs.pop("on_startup", None)
        kwargs.pop("on_shutdown", None)
        self.on_startup = []
        self.on_shutdown = []
        return _orig_router_init(self, *args, **kwargs)

    starlette.routing.Router.__init__ = _patched_router_init
except Exception:
    pass

import pytest

# Ensure ml-pipeline directory is on sys.path and is working directory for test runners executing from any directory
ml_pipeline_root = Path(__file__).resolve().parent.parent
if str(ml_pipeline_root) not in sys.path:
    sys.path.insert(0, str(ml_pipeline_root))
os.chdir(str(ml_pipeline_root))


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
