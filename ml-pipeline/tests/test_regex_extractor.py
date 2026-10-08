from datetime import datetime

from app.domain.log_entry import LogEntry
from app.features.extractors.regex_extractor import RegexExtractor


extractor = RegexExtractor()


def make_log(message: str) -> LogEntry:
    return LogEntry(
        timestamp=datetime.now(),
        level="ERROR",
        service_name="payment-service",
        message=message,
    )


def test_exception():
    result = extractor.extract(
        make_log("java.lang.NullPointerException occurred")
    )

    assert result["contains_exception"] is True


def test_timeout():
    result = extractor.extract(
        make_log("Connection timeout after 30000 ms")
    )

    assert result["contains_timeout"] is True


def test_database():
    result = extractor.extract(
        make_log("SQLException while executing query")
    )

    assert result["contains_database_error"] is True


def test_http_status():
    result = extractor.extract(
        make_log("GET /api/users returned 500")
    )

    assert result["contains_http_status"] is True


def test_ip():
    result = extractor.extract(
        make_log("Connection from 192.168.1.15")
    )

    assert result["contains_ip_address"] is True


def test_uuid():
    result = extractor.extract(
        make_log(
            "CorrelationId=550e8400-e29b-41d4-a716-446655440000"
        )
    )

    assert result["contains_uuid"] is True


def test_docker():
    result = extractor.extract(
        make_log("Docker container started")
    )

    assert result["contains_docker_keyword"] is True


def test_kubernetes():
    result = extractor.extract(
        make_log("Pod restarted by kubelet")
    )

    assert result["contains_kubernetes_keyword"] is True


def test_normal_message():
    result = extractor.extract(
        make_log("User login successful")
    )

    assert result["contains_exception"] is False
    assert result["contains_timeout"] is False