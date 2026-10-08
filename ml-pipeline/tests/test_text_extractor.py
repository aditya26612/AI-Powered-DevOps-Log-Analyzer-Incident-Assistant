from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.extractors.text_extractor import TextExtractor


extractor = TextExtractor()


def test_text_extractor():

    log = LogEntry(
        timestamp=datetime.now(),
        level="INFO",
        service_name="user-service",
        message="User Login SUCCESS 200",
    )

    result = extractor.extract(log)

    assert result["message_length"] > 0

    assert result["token_count"] == 4

    assert result["uppercase_ratio"] > 0

    assert result["digit_ratio"] > 0

    assert result["contains_mixed_case"] is True

    assert result["empty_message"] is False


def test_empty_message():

    log = LogEntry(
        timestamp=datetime.now(),
        level="INFO",
        service_name="user-service",
        message="",
    )

    result = extractor.extract(log)

    assert result["empty_message"] is True

    assert result["token_count"] == 0