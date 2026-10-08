"""
===============================================================================
DevInsight AI - Text Utility Functions
===============================================================================

Provides reusable text analysis functions for feature extraction.

These utilities are shared across multiple feature extractors and contain
only deterministic calculations with no business logic.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import re
from statistics import variance

from app.features.constants import SPECIAL_CHARACTERS
from app.features.utils.tokenizer import tokenize


TIMESTAMP_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}"
)


# =============================================================================
# Basic Token Statistics
# =============================================================================


def average_word_length(text: str) -> float:
    """Return the average token length."""

    tokens = tokenize(text)

    if not tokens:
        return 0.0

    return round(
        sum(len(token) for token in tokens) / len(tokens),
        6,
    )


def longest_token_length(text: str) -> int:
    """Return the longest token length."""

    tokens = tokenize(text)

    if not tokens:
        return 0

    return max(len(token) for token in tokens)


def shortest_token_length(text: str) -> int:
    """Return the shortest token length."""

    tokens = tokenize(text)

    if not tokens:
        return 0

    return min(len(token) for token in tokens)


def average_token_length_variance(text: str) -> float:
    """
    Return the variance of token lengths.

    Returns 0 for zero or one token.
    """

    tokens = tokenize(text)

    if len(tokens) <= 1:
        return 0.0

    lengths = [len(token) for token in tokens]

    return round(variance(lengths), 6)


# =============================================================================
# Character Ratios
# =============================================================================


def uppercase_ratio(text: str) -> float:
    """Return uppercase character ratio."""

    if not text:
        return 0.0

    return round(
        sum(c.isupper() for c in text) / len(text),
        6,
    )


def lowercase_ratio(text: str) -> float:
    """Return lowercase character ratio."""

    if not text:
        return 0.0

    return round(
        sum(c.islower() for c in text) / len(text),
        6,
    )


def alphabetic_ratio(text: str) -> float:
    """Return alphabetic character ratio."""

    if not text:
        return 0.0

    return round(
        sum(c.isalpha() for c in text) / len(text),
        6,
    )


def digit_ratio(text: str) -> float:
    """Return digit ratio."""

    if not text:
        return 0.0

    return round(
        sum(c.isdigit() for c in text) / len(text),
        6,
    )


def special_character_ratio(text: str) -> float:
    """Return special character ratio."""

    if not text:
        return 0.0

    return round(
        sum(c in SPECIAL_CHARACTERS for c in text) / len(text),
        6,
    )


def punctuation_ratio(text: str) -> float:
    """Return punctuation ratio."""

    if not text:
        return 0.0

    punctuation = sum(
        not c.isalnum() and not c.isspace()
        for c in text
    )

    return round(
        punctuation / len(text),
        6,
    )


def whitespace_ratio(text: str) -> float:
    """Return whitespace ratio."""

    if not text:
        return 0.0

    whitespace = sum(c.isspace() for c in text)

    return round(
        whitespace / len(text),
        6,
    )


# =============================================================================
# Diversity Statistics
# =============================================================================


def character_diversity(text: str) -> float:
    """
    Ratio of unique characters.

    0.0 -> repetitive

    1.0 -> all characters unique
    """

    if not text:
        return 0.0

    return round(
        len(set(text)) / len(text),
        6,
    )


def unique_token_ratio(text: str) -> float:
    """Return unique token ratio."""

    tokens = tokenize(text)

    if not tokens:
        return 0.0

    return round(
        len(set(tokens)) / len(tokens),
        6,
    )


# =============================================================================
# Boolean Features
# =============================================================================


def contains_mixed_case(text: str) -> bool:
    """
    True if text contains both uppercase and lowercase letters.
    """

    has_upper = any(c.isupper() for c in text)
    has_lower = any(c.islower() for c in text)

    return has_upper and has_lower


def starts_with_timestamp(text: str) -> bool:
    """
    True if text begins with an ISO-8601 timestamp.
    """

    return bool(TIMESTAMP_PATTERN.match(text))


def empty_message(text: str) -> bool:
    """
    True if message is empty or whitespace.
    """

    return len(text.strip()) == 0


