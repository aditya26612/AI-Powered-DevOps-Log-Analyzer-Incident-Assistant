from app.features.utils.tokenizer import (
    tokenize,
    token_count,
    unique_tokens,
)


def test_empty():
    assert tokenize("") == []


def test_simple():
    tokens = tokenize("User login successful")

    assert tokens == ["User", "login", "successful"]


def test_ip():
    tokens = tokenize("Connection from 192.168.1.10")

    assert "192.168.1.10" in tokens


def test_java():
    tokens = tokenize(
        "java.lang.RuntimeException"
    )

    assert "java.lang.RuntimeException" in tokens


def test_http():
    tokens = tokenize(
        "GET /api/v1/users"
    )

    assert "/api/v1/users" in tokens


def test_service():
    tokens = tokenize(
        "payment-service started"
    )

    assert "payment-service" in tokens


def test_token_count():
    assert token_count("one two three") == 3


def test_unique_tokens():
    assert unique_tokens("a a b") == {"a", "b"}