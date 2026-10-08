"""
===============================================================================
DevInsight AI - Text Feature Extractor
===============================================================================

Extracts textual features from a log message.

Features
--------
- message_length
- token_count
- average_word_length
- uppercase_ratio
- lowercase_ratio
- digit_ratio
- alphabetic_ratio
- special_character_ratio
- longest_token_length
- shortest_token_length
- average_token_length_variance
- whitespace_ratio
- contains_mixed_case
- starts_with_timestamp
- empty_message

Author : DevInsight AI
Version : 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.domain.log_entry import LogEntry
from app.features.contracts.extractor import FeatureExtractor
from app.features.utils.text_utils import (
    alphabetic_ratio,
    average_token_length_variance,
    average_word_length,
    contains_mixed_case,
    digit_ratio,
    empty_message,
    longest_token_length,
    lowercase_ratio,
    shortest_token_length,
    special_character_ratio,
    starts_with_timestamp,
    uppercase_ratio,
    whitespace_ratio,
)
from app.features.utils.tokenizer import token_count


class TextExtractor(FeatureExtractor):
    """
    Extract text-based features from the log message.
    """

    def extract(
        self,
        log: LogEntry,
    ) -> dict[str, float | int | bool]:

        message = log.message

        return {
            "message_length": len(message),
            "token_count": token_count(message),
            "average_word_length": average_word_length(message),
            "uppercase_ratio": uppercase_ratio(message),
            "lowercase_ratio": lowercase_ratio(message),
            "digit_ratio": digit_ratio(message),
            "alphabetic_ratio": alphabetic_ratio(message),
            "special_character_ratio": special_character_ratio(message),
            "longest_token_length": longest_token_length(message),
            "shortest_token_length": shortest_token_length(message),
            "average_token_length_variance": average_token_length_variance(message),
            "whitespace_ratio": whitespace_ratio(message),
            "contains_mixed_case": contains_mixed_case(message),
            "starts_with_timestamp": starts_with_timestamp(message),
            "empty_message": empty_message(message),
        }