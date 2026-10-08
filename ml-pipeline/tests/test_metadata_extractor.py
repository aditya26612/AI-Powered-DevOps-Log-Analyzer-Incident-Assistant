from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.extractors.metadata_extractor import (
    MetadataExtractor,
)


extractor = MetadataExtractor()


def test_metadata():

    log = LogEntry(
        timestamp=datetime.fromisoformat(
            "2026-07-01T10:22:33"
        ),
        level="ERROR",
        service_name="payment-service",
        message="Timeout",
    )

    result = extractor.extract(log)

    assert result["log_level"] == 4

    assert result["hour_of_day"] == 10

    assert 0 <= result["day_of_week"] <= 6

    assert 0 <= result["service_hash"] < 256