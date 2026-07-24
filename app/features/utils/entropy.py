"""
===============================================================================
DevInsight AI - Shannon Entropy Utility
===============================================================================

Provides utility functions for calculating the Shannon entropy of text.

Shannon entropy measures the randomness (information content) of a string.

Typical observations:

- Simple INFO logs              -> Low entropy
- Stack traces                  -> Medium entropy
- Memory dumps                  -> High entropy
- Base64 / Encoded payloads     -> Very high entropy

This utility is used by the Statistical Feature Extractor.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from collections import Counter
from math import log2


def calculate_entropy(text: str) -> float:
    """
    Calculate the Shannon entropy of a text string.

    Parameters
    ----------
    text : str
        Input text.

    Returns
    -------
    float
        Shannon entropy.

    Examples
    --------
    >>> calculate_entropy("aaaaaa")
    0.0

    >>> calculate_entropy("abcdef")
    2.58
    """

    if not text:
        return 0.0

    character_counts = Counter(text)

    text_length = len(text)

    entropy = 0.0

    for count in character_counts.values():
        probability = count / text_length
        entropy -= probability * log2(probability)

    return round(entropy, 6)


def normalized_entropy(text: str) -> float:
    """
    Calculate normalized Shannon entropy.

    Returns a value between 0 and 1.

    Parameters
    ----------
    text : str

    Returns
    -------
    float
    """

    if not text:
        return 0.0

    entropy = calculate_entropy(text)

    max_entropy = log2(len(set(text)))

    if max_entropy == 0:
        return 0.0

    return round(entropy / max_entropy, 6)