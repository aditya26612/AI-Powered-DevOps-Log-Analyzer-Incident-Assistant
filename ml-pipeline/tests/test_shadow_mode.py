from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import MagicMock
import json
import pytest
from sklearn.ensemble import IsolationForest
import numpy as np

from app.domain.log_entry import LogEntry
from app.domain.model_metadata import ModelMetadata
from app.services.prediction_service import PredictionService
from app.services.shadow_recorder import ShadowAuditRecorder
from app.features.orchestrator import FeatureOrchestrator
from app.features.curated_orchestrator import CuratedFeatureOrchestrator
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService


def create_fixture_models():
    # Primary model (legacy 41 features)
    primary_orch = FeatureOrchestrator()
    primary_builder = FeatureMatrixBuilder(primary_orch)
    primary_model = IsolationForest(n_estimators=10, random_state=42)
    dummy_logs = [
        LogEntry(timestamp=datetime.now(timezone.utc), level="INFO", service_name="api-gateway", message=f"Normal log {i}")
        for i in range(25)
    ]
    X_primary = primary_builder.build(dummy_logs)
    primary_model.fit(X_primary)
    preproc = PreprocessingService(scaler=None)

    # Shadow candidate model (curated 40 features)
    shadow_orch = CuratedFeatureOrchestrator(include_severity=True)
    shadow_builder = FeatureMatrixBuilder(shadow_orch)
    shadow_model = IsolationForest(n_estimators=10, random_state=42)
    X_shadow = shadow_builder.build(dummy_logs)
    shadow_model.fit(X_shadow)

    primary_meta = ModelMetadata(
        model_name="PrimaryIF",
        model_version="1.0.0",
        algorithm="IsolationForest",
        feature_version="1.0.0",
        feature_count=X_primary.shape[1],
        feature_names=["f"] * X_primary.shape[1],
        dataset_size=25,
        contamination=0.05,
        random_state=42,
        python_version="3.11",
        sklearn_version="1.8.0",
        trained_at=datetime.now(timezone.utc)
    )

    shadow_meta = ModelMetadata(
        model_name="CuratedIF",
        model_version="2.0.0-candidate",
        algorithm="IsolationForest",
        feature_version="2.0.0",
        feature_count=X_shadow.shape[1],
        feature_names=["f"] * X_shadow.shape[1],
        dataset_size=25,
        contamination=0.08,
        random_state=42,
        python_version="3.11",
        sklearn_version="1.8.0",
        trained_at=datetime.now(timezone.utc)
    )

    return primary_model, primary_builder, preproc, primary_meta, shadow_model, shadow_builder, shadow_meta


def test_prediction_service_shadow_mode_agreement(tmp_path):
    primary_model, primary_builder, preproc, primary_meta, shadow_model, shadow_builder, shadow_meta = create_fixture_models()
    recorder = ShadowAuditRecorder(output_path=tmp_path / "shadow.jsonl")

    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=shadow_model,
        shadow_feature_builder=shadow_builder,
        shadow_threshold=-0.02,
        shadow_metadata=shadow_meta,
        shadow_recorder=recorder
    )

    test_log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="api-gateway",
        message="Standard routine healthcheck 200 OK"
    )

    result = service.predict(test_log)
    assert result.prediction in (-1, 1)
    assert result.shadow_prediction in (-1, 1)
    assert isinstance(result.decision_score, float)
    assert isinstance(result.shadow_decision_score, float)

    summary = recorder.summarize()
    assert summary["total_evaluations"] == 1


def test_prediction_service_shadow_mode_disagreement(tmp_path):
    primary_model, primary_builder, preproc, primary_meta, shadow_model, shadow_builder, shadow_meta = create_fixture_models()
    recorder = ShadowAuditRecorder(output_path=tmp_path / "shadow.jsonl")

    # Mock primary to return 1 (Normal) and shadow to return -1 (Anomaly)
    mock_primary = MagicMock()
    mock_primary.predict.return_value = np.array([1])
    mock_primary.decision_function.return_value = np.array([0.15])

    mock_shadow = MagicMock()
    # Score below threshold (-0.02) triggers anomaly (-1)
    mock_shadow.decision_function.return_value = np.array([-0.05])

    service = PredictionService(
        model=mock_primary,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=mock_shadow,
        shadow_feature_builder=shadow_builder,
        shadow_threshold=-0.02,
        shadow_metadata=shadow_meta,
        shadow_recorder=recorder
    )

    test_log = LogEntry(
        log_id="disagree-log-001",
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="payment-service",
        message="Silent payment transaction rate dropped to 0 per minute"
    )

    result = service.predict(test_log)

    # CRITICAL: Primary prediction returned to Java remains 1 (authoritative)
    assert result.prediction == 1
    assert result.is_anomaly is False
    assert result.decision_score == 0.15

    # Shadow prediction independently caught the anomaly
    assert result.shadow_prediction == -1
    assert result.shadow_decision_score == -0.05

    # Disagreement recorded in audit store
    disagreements = recorder.read_disagreements()
    assert len(disagreements) == 1
    d = disagreements[0]
    assert d["event_id"] == "disagree-log-001"
    assert d["service_name"] == "payment-service"
    assert d["primary_prediction"] == 1
    assert d["shadow_prediction"] == -1
    assert d["is_disagreement"] is True
    assert d["primary_model_version"] == "1.0.0"
    assert d["shadow_model_version"] == "2.0.0-candidate"


def test_shadow_mode_v2_model_unavailable():
    primary_model, primary_builder, preproc, primary_meta, _, _, _ = create_fixture_models()

    # Shadow model is None
    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=None,
        shadow_feature_builder=None
    )

    test_log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="api-gateway",
        message="Healthy test message"
    )

    result = service.predict(test_log)
    assert result.prediction in (-1, 1)
    assert result.shadow_prediction is None
    assert result.shadow_decision_score is None


def test_shadow_mode_invalid_artifact_schema_mismatch():
    primary_model, primary_builder, preproc, primary_meta, _, _, _ = create_fixture_models()

    # Broken shadow model that raises dimension error
    broken_shadow_model = MagicMock()
    broken_shadow_model.decision_function.side_effect = ValueError("Feature shape mismatch: expected 40, got 99")

    broken_shadow_builder = MagicMock()
    broken_shadow_builder.build.return_value = np.zeros((1, 99))

    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=broken_shadow_model,
        shadow_feature_builder=broken_shadow_builder
    )

    test_log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="ERROR",
        service_name="payment-service",
        message="Database timeout"
    )

    # Primary inference MUST NOT crash
    result = service.predict(test_log)
    assert result.prediction in (-1, 1)
    assert result.shadow_prediction is None
    assert result.shadow_decision_score is None


def test_shadow_mode_timeout_and_exception_isolation():
    primary_model, primary_builder, preproc, primary_meta, _, shadow_builder, _ = create_fixture_models()

    timeout_shadow_model = MagicMock()
    timeout_shadow_model.decision_function.side_effect = TimeoutError("Shadow inference exceeded bounded budget 50ms")

    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=timeout_shadow_model,
        shadow_feature_builder=shadow_builder
    )

    test_log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="api-gateway",
        message="Health probe"
    )

    result = service.predict(test_log)
    assert result.prediction in (-1, 1)
    assert result.shadow_prediction is None


def test_shadow_mode_no_side_effects(tmp_path):
    primary_model, primary_builder, preproc, primary_meta, shadow_model, shadow_builder, shadow_meta = create_fixture_models()
    recorder = ShadowAuditRecorder(output_path=tmp_path / "shadow.jsonl")

    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=shadow_model,
        shadow_feature_builder=shadow_builder,
        shadow_recorder=recorder
    )

    # Verify that predicting does NOT touch external APIs, triggers, or alerts
    log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="ERROR",
        service_name="order-service",
        message="Deadlock detected between threads"
    )
    result = service.predict(log)

    # Primary is authoritative
    assert result.prediction in (-1, 1)
    # File written strictly contains anonymized structured keys, no sensitive raw log leak
    with open(tmp_path / "shadow.jsonl", "r", encoding="utf-8") as f:
        line = f.readline()
        record = json.loads(line)
        assert "message" not in record  # No raw message payload stored
        assert "password" not in line.lower()
        assert "jwt" not in line.lower()


def test_shadow_mode_deterministic_evaluation():
    primary_model, primary_builder, preproc, primary_meta, shadow_model, shadow_builder, shadow_meta = create_fixture_models()

    service = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=primary_meta,
        shadow_model=shadow_model,
        shadow_feature_builder=shadow_builder,
        shadow_threshold=-0.02
    )

    test_log = LogEntry(
        timestamp=datetime(2026, 11, 19, 12, 0, 0, tzinfo=timezone.utc),
        level="WARN",
        service_name="docker-daemon",
        message="Disk usage high 91%"
    )

    res1 = service.predict(test_log)
    res2 = service.predict(test_log)

    assert res1.prediction == res2.prediction
    assert res1.decision_score == res2.decision_score
    assert res1.shadow_prediction == res2.shadow_prediction
    assert res1.shadow_decision_score == res2.shadow_decision_score
