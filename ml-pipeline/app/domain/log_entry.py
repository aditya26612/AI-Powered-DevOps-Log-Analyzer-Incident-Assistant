"""
===============================================================================
DevInsight AI - Log Entry Domain Model
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass(slots=True, frozen=True)
class LogEntry:
    """
    Immutable representation of a log entry.
    """

    timestamp: datetime

    level: str

    service_name: str

    message: str

    application: str | None = None

    environment: str | None = None

    thread: str | None = None

    logger: str | None = None

    exception: str | None = None

    correlation_id: str | None = None