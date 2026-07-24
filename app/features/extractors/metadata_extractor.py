"""
===============================================================================
DevInsight AI - Metadata Feature Extractor
===============================================================================
"""

from __future__ import annotations

import hashlib

from app.domain.log_entry import LogEntry
from app.features.constants import (
    LOG_LEVEL_ENCODING,
    SERVICE_HASH_BUCKETS,
    UNKNOWN_LOG_LEVEL,
)
from app.features.contracts.extractor import FeatureExtractor


class MetadataExtractor(FeatureExtractor):
    """
    Extracts metadata features.
    """

    @staticmethod
    def _encode_level(level: str) -> int:
        return LOG_LEVEL_ENCODING.get(
            level.upper(),
            UNKNOWN_LOG_LEVEL,
        )

    @staticmethod
    def _service_hash(service: str) -> int:
        if not service:
            return 0

        digest = hashlib.sha256(
            service.encode("utf-8")
        ).hexdigest()

        return int(digest, 16) % SERVICE_HASH_BUCKETS

    def extract(
        self,
        log: LogEntry,
    ) -> dict[str, float | int]:

        return {
            "log_level": self._encode_level(log.level),
            "service_hash": self._service_hash(
                log.service_name
            ),
            "hour_of_day": log.timestamp.hour,
            "day_of_week": log.timestamp.weekday(),
        }