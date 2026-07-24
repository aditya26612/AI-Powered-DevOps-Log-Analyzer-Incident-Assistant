"""
===============================================================================
DevInsight AI - Log Tokenizer
===============================================================================

Provides tokenization utilities specifically designed for DevOps and
application logs.

Unlike simple whitespace splitting, this tokenizer preserves important
technical tokens such as:

- Java package names
- URLs
- IP addresses
- UUIDs
- HTTP endpoints
- Docker/Kubernetes identifiers
- Hyphenated service names

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import re
from typing import Final

# Matches common DevOps log tokens
TOKEN_PATTERN: Final = re.compile(
    r"""
    [A-Za-z0-9._:/\\-]+
    """,
    re.VERBOSE,
)


def tokenize(text: str) -> list[str]:
    """
    Tokenize a log message.

    Parameters
    ----------
    text : str
        Log message.

    Returns
    -------
    list[str]
        List of extracted tokens.

    Examples
    --------
    >>> tokenize("User login successful")
    ['User', 'login', 'successful']

    >>> tokenize("Connection timeout after 30000 ms")
    ['Connection', 'timeout', 'after', '30000', 'ms']
    """

    if not text:
        return []

    return TOKEN_PATTERN.findall(text)


def token_count(text: str) -> int:
    """
    Return the number of tokens in the text.
    """

    return len(tokenize(text))


def unique_tokens(text: str) -> set[str]:
    """
    Return unique tokens.
    """

    return set(tokenize(text))