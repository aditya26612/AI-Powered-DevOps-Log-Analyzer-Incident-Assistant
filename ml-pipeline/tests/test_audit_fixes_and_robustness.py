from datetime import datetime, timezone
from pathlib import Path
import numpy as np
import pytest

from app.artifacts.artifact_manager import ArtifactManager
from app.bootstrap.training import TrainingBootstrap
from app.core.training_config import TrainingConfig
from app.domain.log_entry import LogEntry
from app.domain.training_result import TrainingResult
from app.features.curated_orchestrator import CuratedFeatureOrchestrator
from app.features.orchestrator import FeatureOrchestrator
from app.models.model_factory import ModelFactory
from app.services.evaluator import Evaluator


def test_curated_orchestrator_adversarial_none_inputs():
    """Verify that CuratedFeatureOrchestrator never crashes on null/none fields."""
    orch = CuratedFeatureOrchestrator(include_severity=True)

    log_null = LogEntry(
        timestamp=None,
        level=None,
        service_name=None,
        message=None
    )
    vec = orch.extract(log_null)
    assert len(vec.values) == len(orch.feature_names)
    assert not np.isnan(vec.values).any()

    # Unknown service flag should be 1.0 when service_name is None
    feat_map = dict(zip(vec.feature_names, vec.values))
    assert feat_map["is_service_unknown"] == 1.0
    assert feat_map["log_level"] == -1.0  # UNKNOWN_LOG_LEVEL is -1


def test_curated_orchestrator_unicode_and_extreme_length():
    """Verify resilience against unicode, emoji, and extremely long log entries."""
    orch = CuratedFeatureOrchestrator(include_severity=True)

    long_msg = "🚨 Fatal cluster outage: \u2620\ufe0f Unicode error with 404 and 500 " + "A" * 5000
    log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="FATAL",
        service_name="payment-service",
        message=long_msg
    )
    vec = orch.extract(log)
    assert len(vec.values) == 40
    feat_map = dict(zip(vec.feature_names, vec.values))
    assert feat_map["is_service_payment_service"] == 1.0
    assert feat_map["message_length"] == len(long_msg)


def test_training_bootstrap_wires_feature_set_and_output_dir(tmp_path):
    """Verify that TrainingBootstrap respects feature_set and model_output_dir from config."""
    custom_dir = tmp_path / "candidate_dir"

    # 1. Curated feature set
    cfg_curated = TrainingConfig(
        feature_set="curated",
        model_output_dir=custom_dir,
        save_artifacts=False
    )
    pipeline_curated = TrainingBootstrap.create(cfg_curated)
    assert isinstance(pipeline_curated._feature_builder._orchestrator, CuratedFeatureOrchestrator)
    assert pipeline_curated._feature_builder._orchestrator.include_severity is True
    assert pipeline_curated._artifact_manager._artifact_directory == custom_dir

    # 2. No-severity feature set
    cfg_no_sev = TrainingConfig(
        feature_set="no_severity",
        model_output_dir=custom_dir,
        save_artifacts=False
    )
    pipeline_no_sev = TrainingBootstrap.create(cfg_no_sev)
    assert isinstance(pipeline_no_sev._feature_builder._orchestrator, CuratedFeatureOrchestrator)
    assert pipeline_no_sev._feature_builder._orchestrator.include_severity is False

    # 3. Legacy feature set
    cfg_legacy = TrainingConfig(
        feature_set="legacy",
        save_artifacts=False
    )
    pipeline_legacy = TrainingBootstrap.create(cfg_legacy)
    assert isinstance(pipeline_legacy._feature_builder._orchestrator, FeatureOrchestrator)


def test_artifact_manager_load_scaler_allow_missing(tmp_path):
    """Verify load_scaler returns None when allow_missing=True and scaler does not exist."""
    mgr = ArtifactManager(artifact_directory=tmp_path)
    # Scaler does not exist in tmp_path
    assert not mgr.scaler_exists()

    # With allow_missing=True, should return None cleanly without FileNotFoundError
    scaler = mgr.load_scaler(allow_missing=True)
    assert scaler is None

    # With allow_missing=False, should raise FileNotFoundError
    with pytest.raises(FileNotFoundError):
        mgr.load_scaler(allow_missing=False)


def test_evaluator_exact_metrics_and_zero_division():
    """Verify exact numerical precision for F1, F2, Precision, Recall and zero division safety."""
    model = ModelFactory.create_model()
    result = TrainingResult(model=model, feature_count=40, sample_count=10, training_time_seconds=0.1)

    # Confusion matrix with known counts:
    # 2 TP, 1 FP, 1 FN, 6 TN
    y_true = np.array([1, 1, 1, 0, 0, 0, 0, 0, 0, 0])
    # Predictions convention: -1 = Anomaly, 1 = Normal
    predictions = np.array([-1, -1, 1, -1, 1, 1, 1, 1, 1, 1])

    report = Evaluator.evaluate(
        result=result,
        predictions=predictions,
        artifacts_saved=False,
        y_true=y_true
    )

    # TP = 2, FP = 1, FN = 1, TN = 6
    # Precision = 2 / (2 + 1) = 0.6667
    # Recall = 2 / (2 + 1) = 0.6667
    # F1 = 2 * 2 / (2*2 + 1 + 1) = 4 / 6 = 0.6667
    # F2 = 5 * 2 / (5*2 + 4*1 + 1) = 10 / 15 = 0.6667
    assert report.precision == pytest.approx(0.6667, abs=1e-4)
    assert report.recall == pytest.approx(0.6667, abs=1e-4)
    assert report.f1_score == pytest.approx(0.6667, abs=1e-4)
    assert report.f2_score == pytest.approx(0.6667, abs=1e-4)
    assert report.confusion_matrix == {"tp": 2, "fp": 1, "fn": 1, "tn": 6}

    # Test all-normal edge case (no anomalies predicted: TP=0, FP=0)
    all_normal_preds = np.ones(10, dtype=int)
    report_normal = Evaluator.evaluate(
        result=result,
        predictions=all_normal_preds,
        artifacts_saved=False,
        y_true=y_true
    )
    assert report_normal.precision == 0.0
    assert report_normal.recall == 0.0
    assert report_normal.f1_score == 0.0
    assert report_normal.f2_score == 0.0


def test_prediction_service_model_scaler_mismatch_raises_value_error():
    """Verify that a model/scaler feature count mismatch fails safely with ValueError."""
    from sklearn.ensemble import IsolationForest
    from sklearn.preprocessing import StandardScaler
    from app.services.prediction_service import PredictionService
    from app.services.preprocessing_service import PreprocessingService
    from app.services.feature_matrix_builder import FeatureMatrixBuilder
    from app.domain.model_metadata import ModelMetadata

    # Scaler fitted on 41 features
    scaler = StandardScaler()
    scaler.fit(np.zeros((10, 41)))

    # Model fitted on 40 features
    model = IsolationForest(n_estimators=5, random_state=42)
    model.fit(np.zeros((10, 40)))

    orch = CuratedFeatureOrchestrator(include_severity=True)
    builder = FeatureMatrixBuilder(orch)
    preproc = PreprocessingService(scaler=scaler)

    meta = ModelMetadata(
        model_name="CuratedIF",
        model_version="2.0.0",
        algorithm="IsolationForest",
        feature_version="2.0.0",
        feature_count=40,
        feature_names=orch.feature_names,
        dataset_size=10,
        contamination=0.05,
        random_state=42,
        python_version="3.11",
        sklearn_version="1.8.0",
        trained_at=datetime.now(timezone.utc)
    )

    with pytest.raises(ValueError, match="model/scaler mismatch"):
        PredictionService(
            model=model,
            preprocessor=preproc,
            feature_builder=builder,
            metadata=meta
        )


def test_prediction_service_model_feature_builder_mismatch_raises_value_error():
    """Verify that a model/feature-builder feature count mismatch fails safely with ValueError."""
    from sklearn.ensemble import IsolationForest
    from app.services.prediction_service import PredictionService
    from app.services.preprocessing_service import PreprocessingService
    from app.services.feature_matrix_builder import FeatureMatrixBuilder
    from app.domain.model_metadata import ModelMetadata

    # Model fitted on 40 features
    model = IsolationForest(n_estimators=5, random_state=42)
    model.fit(np.zeros((10, 40)))

    # Builder with 41 features
    orch_41 = FeatureOrchestrator()
    builder_41 = FeatureMatrixBuilder(orch_41)
    preproc = PreprocessingService(scaler=None)

    meta = ModelMetadata(
        model_name="CuratedIF",
        model_version="2.0.0",
        algorithm="IsolationForest",
        feature_version="1.0.0",
        feature_count=41,
        feature_names=orch_41.feature_names,
        dataset_size=10,
        contamination=0.05,
        random_state=42,
        python_version="3.11",
        sklearn_version="1.8.0",
        trained_at=datetime.now(timezone.utc)
    )

    with pytest.raises(ValueError, match="PredictionService feature mismatch"):
        PredictionService(
            model=model,
            preprocessor=preproc,
            feature_builder=builder_41,
            metadata=meta
        )


def test_prediction_service_shadow_mode_feature_mismatch_disables_shadow_cleanly():
    """Verify that a feature dimension mismatch in candidate shadow model disables shadow mode without failing primary."""
    from sklearn.ensemble import IsolationForest
    from app.services.prediction_service import PredictionService
    from app.services.preprocessing_service import PreprocessingService
    from app.services.feature_matrix_builder import FeatureMatrixBuilder
    from app.domain.model_metadata import ModelMetadata

    # Primary model (40 features)
    primary_model = IsolationForest(n_estimators=5, random_state=42)
    primary_model.fit(np.zeros((10, 40)))
    primary_orch = CuratedFeatureOrchestrator(include_severity=True)
    primary_builder = FeatureMatrixBuilder(primary_orch)
    preproc = PreprocessingService(scaler=None)

    # Incompatible Shadow model fitted on 99 features
    shadow_model = IsolationForest(n_estimators=5, random_state=42)
    shadow_model.fit(np.zeros((10, 99)))

    # But shadow builder provides 40 features
    shadow_builder = FeatureMatrixBuilder(primary_orch)

    meta = ModelMetadata(
        model_name="CuratedIF",
        model_version="2.0.0",
        algorithm="IsolationForest",
        feature_version="2.0.0",
        feature_count=40,
        feature_names=primary_orch.feature_names,
        dataset_size=10,
        contamination=0.05,
        random_state=42,
        python_version="3.11",
        sklearn_version="1.8.0",
        trained_at=datetime.now(timezone.utc)
    )

    # Must NOT raise exception; shadow mode is disabled cleanly
    svc = PredictionService(
        model=primary_model,
        preprocessor=preproc,
        feature_builder=primary_builder,
        metadata=meta,
        shadow_model=shadow_model,
        shadow_feature_builder=shadow_builder
    )

    assert svc._shadow_model is None
    assert svc._shadow_feature_builder is None

    # Primary prediction still succeeds normally
    log = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="api-gateway",
        message="Health probe OK"
    )
    result = svc.predict(log)
    assert result.prediction in (-1, 1)
    assert result.shadow_prediction is None
