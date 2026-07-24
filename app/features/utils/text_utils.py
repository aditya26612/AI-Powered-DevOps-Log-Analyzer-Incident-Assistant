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


from statistics import variance

from app.features.constants import SPECIAL_CHARACTERS
from app.features.patterns import TIMESTAMP_PATTERNS
from app.features.utils.tokenizer import tokenize

# =============================================================================
# Timestamp Patterns
# =============================================================================

# Supported formats:
#
# 2026-07-01T10:22:33Z
# 2026-07-01T10:22:33.123Z
# 2026-07-01T10:22:33+05:30
# 2026-07-01 10:22:33
# 2026/07/01 10:22:33
# Jul 15 10:22:33
# January 15 10:22:33
#


# =============================================================================
# Token Statistics
# =============================================================================


def average_word_length(text: str) -> float:
    """
    Calculate average token length.
    """

    tokens = tokenize(text)

    if not tokens:
        return 0.0

    return round(
        sum(len(token) for token in tokens) / len(tokens),
        6,
    )


def longest_token_length(text: str) -> int:
    """
    Length of the longest token.
    """

    tokens = tokenize(text)

    if not tokens:
        return 0

    return max(len(token) for token in tokens)


def shortest_token_length(text: str) -> int:
    """
    Length of the shortest token.
    """

    tokens = tokenize(text)

    if not tokens:
        return 0

    return min(len(token) for token in tokens)


def average_token_length_variance(text: str) -> float:
    """
    Variance of token lengths.

    Returns
    -------
    float
        0.0 when fewer than two tokens exist.
    """

    tokens = tokenize(text)

    if len(tokens) <= 1:
        return 0.0

    lengths = [len(token) for token in tokens]

    return round(
        variance(lengths),
        6,
    )


# =============================================================================
# Character Ratios
# =============================================================================


def uppercase_ratio(text: str) -> float:
    """
    Uppercase characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        sum(c.isupper() for c in text) / len(text),
        6,
    )


def lowercase_ratio(text: str) -> float:
    """
    Lowercase characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        sum(c.islower() for c in text) / len(text),
        6,
    )


def alphabetic_ratio(text: str) -> float:
    """
    Alphabetic characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        sum(c.isalpha() for c in text) / len(text),
        6,
    )


def digit_ratio(text: str) -> float:
    """
    Digit characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        sum(c.isdigit() for c in text) / len(text),
        6,
    )


def special_character_ratio(text: str) -> float:
    """
    Special characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        sum(c in SPECIAL_CHARACTERS for c in text) / len(text),
        6,
    )


def punctuation_ratio(text: str) -> float:
    """
    Punctuation characters / total characters.
    """

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
    """
    Whitespace characters / total characters.
    """

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
    Unique characters / total characters.
    """

    if not text:
        return 0.0

    return round(
        len(set(text)) / len(text),
        6,
    )


def unique_token_ratio(text: str) -> float:
    """
    Unique tokens / total tokens.
    """

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
    True if both uppercase and lowercase letters exist.
    """

    has_upper = any(c.isupper() for c in text)
    has_lower = any(c.islower() for c in text)

    return has_upper and has_lower


def starts_with_timestamp(text: str) -> bool:
    """
    Detect whether a log begins with a supported timestamp.

    Supported formats:

    - 2026-07-01T10:22:33Z
    - 2026-07-01T10:22:33.123Z
    - 2026-07-01T10:22:33+05:30
    - 2026-07-01 10:22:33
    - 2026/07/01 10:22:33
    - Jul 15 10:22:33
    - January 15 10:22:33
    """

    if not text:
        return False

    return any(
        pattern.match(text)
        for pattern in TIMESTAMP_PATTERNS
    )


def empty_message(text: str) -> bool:
    """
    True if message is empty or contains only whitespace.
    """

    return text.strip() == ""


# =============================================================================
# Additional Statistical Features
# =============================================================================


def numeric_density(text: str) -> float:
    """
    Ratio of numeric characters to total characters.
    """

    if not text:
        return 0.0

    digits = sum(c.isdigit() for c in text)

    return round(
        digits / len(text),
        6,
    )


def average_line_length(text: str) -> float:
    """
    Average length of lines in the log.
    """

    if not text:
        return 0.0

    lines = text.splitlines()

    if not lines:
        return 0.0

    return round(
        sum(len(line) for line in lines) / len(lines),
        6,
    )


def line_count(text: str) -> int:
    """
    Number of lines in the log.
    """

    if not text:
        return 0

    return len(text.splitlines())