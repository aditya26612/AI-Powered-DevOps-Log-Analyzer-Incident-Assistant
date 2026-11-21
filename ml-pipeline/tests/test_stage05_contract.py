from datetime import datetime
from hashlib import sha256
import json
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from app.bootstrap.prediction import load_bundle
from app.domain.feature_vector import FeatureVector
from app.domain.log_entry import LogEntry
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.prediction_service import PredictionService
from app.services.preprocessing_service import PreprocessingService

FIXTURE = json.loads(Path(__file__).with_name("data").joinpath("stage05-v2-golden.json").read_text(encoding="utf-8"))
ARTIFACT = Path("artifacts/candidates/v2_curated_isolation_forest/model.pkl")
EVENT = {"timestamp": "2026-11-19T10:00:00Z", "level": "INFO",
         "service_name": "payment-service", "message": "Application started successfully"}


@pytest.fixture(scope="module")
def v2():
    model, builder, scaler, metadata = load_bundle(ARTIFACT, "v2")
    return PredictionService(model, PreprocessingService(scaler), builder, metadata, FIXTURE["threshold"])


@pytest.mark.parametrize("case", FIXTURE["cases"], ids=lambda case: case["id"])
def test_golden_vectors_and_scores(v2, case):
    log = LogEntry(datetime.fromisoformat(case["timestamp"]), case["level"], case["service_name"], case["message"])
    vector = v2._feature_builder.build([log])[0]
    result = v2.predict(log)
    assert sha256(ARTIFACT.read_bytes()).hexdigest() == FIXTURE["artifact_sha"]
    assert v2.metadata.feature_names == FIXTURE["ordered_names"]
    assert v2.metadata.model_version == FIXTURE["model"]
    assert vector.shape == (40,)
    assert vector[1] == len(case["message"])
    assert vector[32] == 10  # UTC hour at position 32 in the immutable feature contract.
    assert sum(vector[-7:]) == 1
    if case["vector"] is not None:
        assert vector == pytest.approx(case["vector"], rel=0, abs=FIXTURE["numeric_tolerance"])
        assert result.decision_score == pytest.approx(case["score"], rel=0, abs=FIXTURE["numeric_tolerance"])
    assert result.is_anomaly == case["statistical_anomaly"]
    assert (case["level"] in ("ERROR", "FATAL")) == case["operational_critical"]


@pytest.mark.parametrize("value", [float("nan"), float("inf"), -float("inf"), 1e20, "1.0"])
def test_invalid_features_do_not_become_successful_matrix_values(value):
    builder = FeatureMatrixBuilder(SimpleNamespace(feature_names=["x"],
        extract=lambda _: FeatureVector([value], ["x"])))
    with pytest.raises(ValueError, match="feature values"):
        builder.build([None])


def test_score_shape_and_non_finite_outputs_are_rejected(v2, monkeypatch):
    log = LogEntry(datetime.fromisoformat(EVENT["timestamp"]), EVENT["level"], EVENT["service_name"], EVENT["message"])
    for bad in (np.array([float("nan")]), np.array([float("inf")]), np.array([[0.1]]), np.array([2.0])):
        monkeypatch.setattr(v2._model, "decision_function", lambda _: bad)
        with pytest.raises(ValueError, match="Invalid model decision scores"):
            v2.predict(log)


def test_bootstrap_rejects_corrupt_artifact_before_loading(tmp_path):
    (tmp_path / "metadata.json").write_bytes(ARTIFACT.with_name("metadata.json").read_bytes())
    invalid = tmp_path / "model.pkl"
    invalid.write_bytes(b"not a trained artifact")
    with pytest.raises(ValueError, match="checksum"):
        load_bundle(invalid, "v2")


@pytest.mark.parametrize("replacement", [
    {"timestamp": 1730000000}, {"timestamp": "not-a-date"}, {"message": 1},
    {"message": " "}, {"message": "\x00"}, {"message": "x" * 16_385},
])
def test_invalid_http_request_returns_sanitized_422(prediction_client, replacement):
    response = prediction_client.post("/api/v1/predict", json=EVENT | replacement)
    assert response.status_code == 422
    assert response.json()["message"] == "Request validation failed."


def test_iso_offset_batch_order_and_input_limits(prediction_client):
    path = "/api/v1/predict"
    later_arrival = EVENT | {"timestamp": "2026-11-19T15:30:00+05:30"}
    older = EVENT | {"timestamp": "2026-11-18T08:00:00Z", "message": "GET /health 200 OK"}
    single = prediction_client.post(path, json=EVENT)
    batch = prediction_client.post(path + "/batch", json={"logs": [later_arrival, older]})
    assert single.status_code == batch.status_code == 200
    assert batch.json()["predictions"] == [single.json(), prediction_client.post(path, json=older).json()]
    assert prediction_client.post(path, json=EVENT | {"message": "界" * 16_384}).status_code == 200
    maximum = prediction_client.post(path + "/batch", json={"logs": [EVENT] * 32})
    assert maximum.status_code == 200 and len(maximum.json()["predictions"]) == 32
    assert prediction_client.post(path + "/batch", json={"logs": [EVENT] * 33}).status_code == 422
    assert prediction_client.post(path, content=b"x" * (4 * 1024 * 1024 + 1),
                                  headers={"Content-Type": "application/json"}).status_code == 413


def test_busy_inference_rejects_both_routes_and_recovers(prediction_client):
    from app.api.v1.routes.prediction import _slots
    assert _slots.acquire(blocking=False)
    assert _slots.acquire(blocking=False)
    try:
        assert prediction_client.post("/api/v1/predict", json=EVENT).status_code == 503
        assert prediction_client.post("/api/v1/predict/batch", json={"logs": [EVENT]}).status_code == 503
    finally:
        _slots.release()
        _slots.release()
    assert prediction_client.post("/api/v1/predict", json=EVENT).status_code == 200
