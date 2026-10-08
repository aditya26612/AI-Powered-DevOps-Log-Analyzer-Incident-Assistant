from app.features.utils.entropy import (
    calculate_entropy,
    normalized_entropy,
)


def test_empty_string():
    assert calculate_entropy("") == 0.0


def test_single_character():
    assert calculate_entropy("aaaaaa") == 0.0


def test_random_string():
    entropy = calculate_entropy("abcdef")

    assert entropy > 2


def test_normalized_entropy():
    value = normalized_entropy("abcdef")

    assert 0 <= value <= 1