from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
from threading import Event

import pytest

from app.bootstrap import prediction
from app.core.config import Settings
from app.domain.log_entry import LogEntry


def test_reordered_features_and_missing_artifact(tmp_path):
    source = Path(Settings(ACTIVE_MODEL="v2").MODEL_PATH)
    shutil.copy(source.with_name("metadata.json"), tmp_path)
    path = tmp_path / "model.pkl"
    with pytest.raises(FileNotFoundError):
        prediction.load_bundle(path, "v2")
    metadata = json.loads((tmp_path / "metadata.json").read_text())
    metadata["feature_names"].reverse()
    (tmp_path / "metadata.json").write_text(json.dumps(metadata))
    with pytest.raises(ValueError, match="ordered feature"):
        prediction.load_bundle(path, "v2")


def test_same_candidate_and_corrupt_candidate(monkeypatch, tmp_path):
    settings = Settings(ACTIVE_MODEL="v2", SHADOW_MODE_ENABLED=True)
    monkeypatch.setattr(prediction, "get_settings", lambda: settings)
    assert prediction.PredictionBootstrap.create()._shadow_model is None
    settings = Settings(ACTIVE_MODEL="v1", SHADOW_MODE_ENABLED=True,
                        CANDIDATE_MODEL_PATH=str(tmp_path / "model.pkl"),
                        CANDIDATE_METADATA_PATH=str(tmp_path / "metadata.json"))
    (tmp_path / "model.pkl").write_bytes(b"invalid")
    (tmp_path / "metadata.json").write_text("{}")
    assert prediction.PredictionBootstrap.create()._shadow_model is None


def test_serving_shadow_bounded_and_sanitized(monkeypatch, tmp_path):
    settings = Settings(ACTIVE_MODEL="v1", SHADOW_MODE_ENABLED=True)
    monkeypatch.setattr(prediction, "get_settings", lambda: settings)
    output = tmp_path / "audit.jsonl"
    from app.services.shadow_recorder import ShadowAuditRecorder
    monkeypatch.setattr(prediction, "ShadowAuditRecorder", lambda: ShadowAuditRecorder(output))
    service = prediction.PredictionBootstrap.create()
    entered, release = Event(), Event()
    def delayed(matrix):
        entered.set()
        if not release.wait(5):
            raise TimeoutError()
        return [-1.0]
    monkeypatch.setattr(service._shadow_model, "decision_function", delayed)
    log = LogEntry(timestamp=datetime.now(timezone.utc), level="INFO", message="private-marker",
                   service_name="private-marker", log_id="private-marker")
    try:
        first = service.predict(log)
        assert entered.wait(2)
        second = service.predict(log)
        assert not output.exists()
        assert first.prediction == second.prediction
        assert first.shadow_prediction is None
    finally:
        release.set()
        service.close()
    lines = output.read_text().splitlines()
    assert len(lines) == 1 and "private-marker" not in lines[0]
    row = json.loads(lines[0])
    assert (row["primary_model_version"], row["shadow_model_version"]) == ("1.0.0", "2.0.0-candidate")
    assert row["shadow_prediction"] == -1
