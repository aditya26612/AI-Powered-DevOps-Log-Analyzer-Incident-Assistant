from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.extractors.statistical_extractor import (
    StatisticalExtractor,
)

extractor = StatisticalExtractor()


def make_log(message: str) -> LogEntry:
    return LogEntry(
        timestamp=datetime.now(),
        level="INFO",
        service_name="user-service",
        message=message,
    )


def test_statistical_features():

    log = make_log(
        "ERROR 500\nConnection timeout\nRetrying request"
    )

    result = extractor.extract(log)

    assert result["entropy"] >= 0.0

    assert result["unique_token_ratio"] > 0

    assert result["character_diversity"] > 0

    assert result["punctuation_ratio"] >= 0

    assert result["numeric_density"] > 0

    assert result["average_line_length"] > 0

    assert result["line_count"] == 3


def test_empty_log():

    result = extractor.extract(
        make_log("")
    )

    assert result["entropy"] == 0.0

    assert result["line_count"] == 0

    assert result["numeric_density"] == 0.0