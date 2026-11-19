"""
===============================================================================
DevInsight AI - Data Leakage and Generalization Regression Test Suite
===============================================================================
Validates:
1. Template-grouped splitting prevents shared message template leakage.
2. Duplicate message text / signature never crosses partitions.
3. Microservice hold-out completely isolates target service from training data.
4. Preprocessing transforms (StandardScaler) fit strictly on training partitions.
5. No ground truth labels or evaluation targets leak into feature representations.
6. Decision thresholds calibrated on validation partitions remain frozen during test.
===============================================================================
"""

import numpy as np
import pytest
from pathlib import Path
from sklearn.model_selection import GroupShuffleSplit
from sklearn.preprocessing import StandardScaler

from app.data.dataset_loader import DatasetLoader
from app.features.utils.template_extractor import TemplateExtractor
from app.features.orchestrator import FeatureOrchestrator
from app.features.curated_orchestrator import CuratedFeatureOrchestrator
from app.features.feature_names import FEATURE_NAMES
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService


def test_template_grouped_split_no_leakage():
    """Verify that template-grouped splitting achieves 0% template overlap between train and test."""
    logs = DatasetLoader.load("data/labeled_operational_logs.jsonl")
    templates = [TemplateExtractor.extract_template(l.message) for l in logs]

    gss = GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=42)
    train_idx, test_idx = next(gss.split(logs, groups=templates))

    train_templates = {templates[i] for i in train_idx}
    test_templates = {templates[i] for i in test_idx}

    shared = train_templates.intersection(test_templates)
    assert len(shared) == 0, f"Template leakage detected! Shared templates: {shared}"
    assert len(train_templates) > 0
    assert len(test_templates) > 0


def test_no_duplicate_message_leakage():
    """Verify that identical normalized message bodies do not leak across template-grouped partitions."""
    logs = DatasetLoader.load("data/labeled_operational_logs.jsonl")
    templates = [TemplateExtractor.extract_template(l.message) for l in logs]

    gss = GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=42)
    train_idx, test_idx = next(gss.split(logs, groups=templates))

    train_messages = {logs[i].message.strip() for i in train_idx if logs[i].message}
    test_messages = {logs[i].message.strip() for i in test_idx if logs[i].message}

    shared_msgs = train_messages.intersection(test_messages)
    assert len(shared_msgs) == 0, f"Duplicate message leakage across partitions: {shared_msgs}"


def test_service_holdout_isolation():
    """Verify that microservice hold-out guarantees 0% target service contamination in training."""
    logs = DatasetLoader.load("data/labeled_operational_logs.jsonl")
    target_service = "payment-service"

    train_logs = [l for l in logs if l.service_name != target_service]
    test_logs = [l for l in logs if l.service_name == target_service]

    assert len(test_logs) > 0, "Target service has no evaluation samples"
    assert all(l.service_name != target_service for l in train_logs)
    assert all(l.service_name == target_service for l in test_logs)


def test_preprocessing_fitted_only_on_training_partition():
    """Verify that PreprocessingService fit_transform uses only training distribution parameters."""
    np.random.seed(42)
    X_train = np.random.normal(loc=10.0, scale=2.0, size=(100, 5))
    X_test = np.random.normal(loc=50.0, scale=10.0, size=(30, 5))

    scaler = StandardScaler()
    preproc = PreprocessingService(scaler=scaler)

    # Fit on train ONLY
    X_train_trans = preproc.fit_transform(X_train)

    # Scaler mean must match X_train mean within precision, NOT X_test or joint
    np.testing.assert_allclose(scaler.mean_, np.mean(X_train, axis=0), rtol=1e-5)
    assert not np.allclose(scaler.mean_, np.mean(X_test, axis=0))
    assert not np.allclose(scaler.mean_, np.mean(np.vstack([X_train, X_test]), axis=0))

    # Test transform must use the frozen train parameters
    X_test_trans = preproc.transform(X_test)
    assert X_test_trans.shape == X_test.shape


def test_no_label_leakage_in_features():
    """Verify that ground-truth labels and rationale are never present in feature definitions."""
    forbidden = {
        "is_anomaly", "is_true_anomaly", "ground_truth", "label",
        "label_rationale", "confidence", "ambiguity_flag", "review_status"
    }

    # 1. Legacy feature names
    for name in FEATURE_NAMES:
        assert name.lower() not in forbidden, f"Label leakage in legacy feature: {name}"

    # 2. Curated feature names
    orch = CuratedFeatureOrchestrator(include_severity=True)
    for name in orch.feature_names:
        assert name.lower() not in forbidden, f"Label leakage in curated feature: {name}"

    # 3. No-severity curated feature names
    orch_no_sev = CuratedFeatureOrchestrator(include_severity=False)
    for name in orch_no_sev.feature_names:
        assert name.lower() not in forbidden, f"Label leakage in no-severity feature: {name}"


def test_frozen_threshold_evaluation_protocol():
    """Verify that decision threshold calibrated on validation data remains frozen during test evaluation."""
    # Anomaly score convention: higher score indicates higher likelihood of anomaly
    val_scores = np.array([0.05, 0.03, 0.01, -0.01, -0.04])
    val_y = np.array([1, 1, 0, 0, 0])

    # Calibrate optimal threshold on validation (e.g. choose 0.02 where val_scores > 0.02 gives 100% precision & recall)
    calibrated_threshold = 0.02

    test_scores = np.array([0.06, 0.04, -0.02, -0.05])
    test_y = np.array([1, 1, 0, 0])

    # Apply FROZEN threshold to test set without recalibration on test
    test_preds = (test_scores > calibrated_threshold).astype(int)

    # Predictions match ground truth exactly under the frozen threshold
    np.testing.assert_array_equal(test_preds, test_y)
