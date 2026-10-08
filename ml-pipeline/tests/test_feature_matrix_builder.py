from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.orchestrator import FeatureOrchestrator
from app.services.feature_matrix_builder import FeatureMatrixBuilder


def test_feature_matrix_builder():

    logs = [
        LogEntry(
            timestamp=datetime.now(),
            level="INFO",
            service_name="user-service",
            message="Application started",
        ),
        LogEntry(
            timestamp=datetime.now(),
            level="ERROR",
            service_name="payment-service",
            message="SQLException occurred",
        ),
    ]

    builder = FeatureMatrixBuilder(
        FeatureOrchestrator()
    )

    matrix = builder.build(logs)

    assert matrix.shape[0] == 2