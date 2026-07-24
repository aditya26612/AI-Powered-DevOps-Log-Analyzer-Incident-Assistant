from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.feature_names import FEATURE_COUNT
from app.features.orchestrator import FeatureOrchestrator


def test_orchestrator():

    orchestrator = FeatureOrchestrator()

    log = LogEntry(
        timestamp=datetime.now(),
        level="ERROR",
        service_name="payment-service",
        message="SQLException occurred while connecting to database",
    )

    vector = orchestrator.extract(log)

    assert len(vector.values) == FEATURE_COUNT

    assert len(vector.feature_names) == FEATURE_COUNT

    assert vector.feature_names[0] == "log_level"

    assert vector.feature_names[-1] == "line_count"

    assert all(isinstance(v, float) for v in vector.values)