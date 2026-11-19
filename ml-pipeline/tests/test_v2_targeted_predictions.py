"""
Targeted Prediction Tests for DevInsight V2 Activation.

Validates V2 Curated Isolation Forest on:
1. Normal operational logs (should be classified as normal: prediction=1, is_anomaly=False)
2. Benign error logs (handled routine errors, not incidents: prediction=1, is_anomaly=False)
3. Operational incident examples (deadlocks, pool exhaustion, OOM: prediction=-1, is_anomaly=True)
4. Schema & feature contract verification (40 curated features, rejection of legacy 41-feature vectors)
5. Rollback configuration verification (ACTIVE_MODEL switch)
"""

import pytest
from datetime import datetime, timezone
from app.bootstrap.prediction import PredictionBootstrap
from app.bootstrap.system import SystemBootstrap
from app.core.config import Settings
from app.domain.log_entry import LogEntry
from app.features.orchestrator import FeatureOrchestrator
from app.features.curated_orchestrator import CuratedFeatureOrchestrator
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.prediction_service import PredictionService


@pytest.fixture(scope="module")
def prediction_service():
    return PredictionBootstrap.create()


@pytest.fixture(scope="module")
def system_service():
    return SystemBootstrap.create()


def test_v2_model_metadata_and_threshold(prediction_service, system_service):
    """Verify FastAPI loads V2's exact metadata, 40 features, and -0.02 threshold."""
    meta = prediction_service.metadata
    assert meta.model_name == "CuratedIsolationForest"
    assert meta.model_version == "2.0.0-candidate"
    assert meta.feature_version == "2.0.0"
    assert meta.feature_count == 40
    assert len(meta.feature_names) == 40
    assert prediction_service.threshold == -0.02

    health = system_service.get_health()
    assert health.status == "UP"
    assert health.model_loaded is True
    assert health.model_version == "2.0.0-candidate"
    assert health.artifacts.model is True
    assert health.artifacts.metadata is True

    model_info = system_service.get_model_info()
    assert model_info.model_name == "CuratedIsolationForest"
    assert model_info.feature_count == 40
    assert model_info.calibrated_decision_threshold == -0.02


def test_v2_normal_operational_logs(prediction_service):
    """Normal operational logs should be classified as inliers (prediction=1, is_anomaly=False)."""
    normal_logs = [
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="user-service",
            message="User authenticated successfully for user_id=48291",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="payment-service",
            message="Payment transaction TX-9281 approved via Stripe gateway",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="order-service",
            message="Order ORD-1092 status updated to PROCESSING in database",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="api-gateway",
            message="HTTP GET /api/v1/users/profile completed 200 OK in 14ms",
        ),
    ]

    results = prediction_service.predict_batch(normal_logs)
    for idx, res in enumerate(results):
        assert res.prediction == 1, f"Normal log {idx} was falsely flagged as anomaly: score={res.decision_score}"
        assert res.is_anomaly is False
        assert res.decision_score >= prediction_service.threshold


def test_v2_benign_errors_not_alerting(prediction_service):
    """Benign or expected validation errors should not trigger high-severity anomaly alerts."""
    benign_logs = [
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="WARN",
            service_name="user-service",
            message="Form validation failed: invalid zip code format entered by client",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="WARN",
            service_name="api-gateway",
            message="Client request path /favicon.ico returned HTTP 404 not found",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="user-service",
            message="User login attempt 1 of 5 failed due to bad password credentials",
        ),
    ]

    results = prediction_service.predict_batch(benign_logs)
    for idx, res in enumerate(results):
        # Benign errors should remain within normal operating score boundary
        assert res.decision_score >= prediction_service.threshold, (
            f"Benign error {idx} dipped below threshold: score={res.decision_score}"
        )
        assert res.prediction == 1
        assert res.is_anomaly is False


def test_v2_operational_incident_examples(prediction_service):
    """Critical operational incidents should be flagged as anomalies (prediction=-1, is_anomaly=True)."""
    incident_logs = [
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="ERROR",
            service_name="order-service",
            message="Fatal connection pool exhausted: HikariPool-1 unable to acquire JDBC connection within 30000ms",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="ERROR",
            service_name="payment-service",
            message="Database deadlock detected: SQL transaction aborted on postgres table payments sqlstate 40001",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="FATAL",
            service_name="user-service",
            message="java.lang.OutOfMemoryError: Java heap space during bulk token refresh",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="ERROR",
            service_name="api-gateway",
            message="SocketTimeoutException: Upstream service payment-service unreachable after 15000ms connection timeout",
        ),
    ]

    results = prediction_service.predict_batch(incident_logs)
    for idx, res in enumerate(results):
        assert res.prediction == -1, f"Operational incident {idx} missed: score={res.decision_score}"
        assert res.is_anomaly is True
        assert res.decision_score < prediction_service.threshold


def test_v2_rejects_legacy_v1_features(prediction_service):
    """V2 model must refuse to run if fed legacy 41-feature vectors."""
    legacy_builder = FeatureMatrixBuilder(FeatureOrchestrator())  # 41 features
    curated_builder = FeatureMatrixBuilder(CuratedFeatureOrchestrator(include_severity=True))  # 40 features

    assert len(legacy_builder._orchestrator.feature_names) == 41
    assert len(curated_builder._orchestrator.feature_names) == 40

    # Passing legacy 41-feature builder to V2 model expects ValueError
    with pytest.raises(ValueError, match="feature mismatch"):
        PredictionService(
            model=prediction_service._model,
            preprocessor=prediction_service._preprocessor,
            feature_builder=legacy_builder,
            metadata=prediction_service._metadata,
        )


def test_rollback_configuration_switch():
    """Verify documented configuration switch cleanly switches between V2 and V1 without overwriting artifacts."""
    # V2 Config
    s_v2 = Settings(ACTIVE_MODEL="v2")
    assert s_v2.ACTIVE_MODEL == "v2"
    assert "candidates" in s_v2.MODEL_PATH
    assert s_v2.ANOMALY_THRESHOLD == -0.02

    # V1 Config (Rollback)
    s_v1 = Settings(ACTIVE_MODEL="v1")
    assert s_v1.ACTIVE_MODEL == "v1"
    assert "candidates" not in s_v1.MODEL_PATH
    assert s_v1.ANOMALY_THRESHOLD == 0.0


def test_v2_direct_41_feature_array_rejection(prediction_service):
    """Raw IsolationForest estimator must reject 41-feature inputs with ValueError."""
    import numpy as np
    bad_matrix = np.zeros((1, 41), dtype=float)
    with pytest.raises(ValueError):
        prediction_service._model.decision_function(bad_matrix)


def test_v2_edge_case_inputs(prediction_service):
    """V2 pipeline must handle edge cases gracefully: empty message, unknown service, unicode, multiline."""
    edge_logs = [
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="completely-unknown-microservice",
            message="",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="DEBUG",
            service_name="api-gateway",
            message="Unicode payload: \u2603 \u2764 \u30c6\u30b9\u30c8 test ok",
        ),
        LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="user-service",
            message="Line 1\nLine 2\nLine 3 multi-line normal statement",
        ),
    ]
    results = prediction_service.predict_batch(edge_logs)
    assert len(results) == 3
    for res in results:
        assert isinstance(res.prediction, int)
        assert isinstance(res.decision_score, float)


def test_shadow_mode_initialization(monkeypatch):
    """Verify PredictionBootstrap with SHADOW_MODE_ENABLED=True initializes without errors."""
    from app.core.config import get_settings
    monkeypatch.setenv("ACTIVE_MODEL", "v1")
    monkeypatch.setenv("SHADOW_MODE_ENABLED", "true")
    get_settings.cache_clear()
    try:
        service_with_shadow = PredictionBootstrap.create()
        assert service_with_shadow._shadow_model is not None
        assert service_with_shadow._shadow_metadata is not None
        assert service_with_shadow._shadow_metadata.feature_count == 40
    finally:
        monkeypatch.delenv("SHADOW_MODE_ENABLED", raising=False)
        get_settings.cache_clear()


def test_rollback_execution_v1_prediction(monkeypatch):
    """Verify setting ACTIVE_MODEL=v1 boots V1 model, 41 features, and predicts successfully."""
    from app.core.config import get_settings
    monkeypatch.setenv("ACTIVE_MODEL", "v1")
    get_settings.cache_clear()
    try:
        v1_service = PredictionBootstrap.create()
        assert v1_service.metadata.model_version == "1.0.0"
        assert v1_service.metadata.feature_count == 41
        assert v1_service.threshold == 0.0
        assert v1_service._preprocessor.scaler is not None

        # Predict normal log entry with V1
        sample_log = LogEntry(
            timestamp=datetime.now(timezone.utc),
            level="INFO",
            service_name="user-service",
            message="User authenticated successfully",
        )
        res = v1_service.predict(sample_log)
        assert res.prediction == 1
        assert res.is_anomaly is False
        assert res.decision_score >= 0.0
    finally:
        monkeypatch.delenv("ACTIVE_MODEL", raising=False)
        get_settings.cache_clear()
