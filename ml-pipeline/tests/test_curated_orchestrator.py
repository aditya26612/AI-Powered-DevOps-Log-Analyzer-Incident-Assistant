from datetime import datetime, timezone
from app.domain.log_entry import LogEntry
from app.features.curated_orchestrator import CuratedFeatureOrchestrator, CURATED_CORE_FEATURE_NAMES


def test_curated_orchestrator_feature_counts():
    orch_with_sev = CuratedFeatureOrchestrator(include_severity=True)
    orch_no_sev = CuratedFeatureOrchestrator(include_severity=False)

    assert len(orch_with_sev.feature_names) == len(CURATED_CORE_FEATURE_NAMES) + 1
    assert "log_level" in orch_with_sev.feature_names
    assert len(orch_no_sev.feature_names) == len(CURATED_CORE_FEATURE_NAMES)
    assert "log_level" not in orch_no_sev.feature_names


def test_curated_orchestrator_zero_variance_eliminated():
    orch = CuratedFeatureOrchestrator()
    names = orch.feature_names

    # Assert known zero-variance features from audit are eliminated
    assert "day_of_week" not in names
    assert "starts_with_timestamp" not in names
    assert "empty_message" not in names
    assert "contains_uuid" not in names
    assert "line_count" not in names

    # Assert collinear duplicates eliminated
    assert "numeric_density" not in names
    assert "average_line_length" not in names


def test_curated_orchestrator_service_categoricals():
    orch = CuratedFeatureOrchestrator(include_severity=True)

    log_known = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="ERROR",
        service_name="payment-service",
        message="HikariPool timeout"
    )
    vec_known = orch.extract(log_known)
    feat_dict_known = dict(zip(vec_known.feature_names, vec_known.values))

    assert feat_dict_known["is_service_payment_service"] == 1.0
    assert feat_dict_known["is_service_user_service"] == 0.0
    assert feat_dict_known["is_service_unknown"] == 0.0

    log_unknown = LogEntry(
        timestamp=datetime.now(timezone.utc),
        level="INFO",
        service_name="quantum-billing-engine",
        message="Started worker"
    )
    vec_unknown = orch.extract(log_unknown)
    feat_dict_unknown = dict(zip(vec_unknown.feature_names, vec_unknown.values))

    assert feat_dict_unknown["is_service_unknown"] == 1.0
    assert feat_dict_unknown["is_service_payment_service"] == 0.0
