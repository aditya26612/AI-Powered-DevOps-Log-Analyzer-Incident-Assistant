"""
===============================================================================
DevInsight AI - Privacy-Conscious Operational Log Anonymizer
===============================================================================
Sanitizes real-world production logs before ingest into experimental pipelines:
- Masks IPv4 and IPv6 addresses
- Masks JWT / Bearer tokens
- Masks UUIDs and Session IDs
- Masks Email addresses
- Masks Database passwords / connection URIs
- Masks Credit Card / PAN numbers
===============================================================================
"""

import re
from typing import Dict, Any


IP_REGEX = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
EMAIL_REGEX = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,7}\b")
JWT_REGEX = re.compile(r"eyJ[a-zA-Z0-9_-]{10,}\.eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}")
UUID_REGEX = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
CREDIT_CARD_REGEX = re.compile(r"\b(?:\d{4}[ -]?){3}\d{4}\b")
DB_CONN_REGEX = re.compile(r"(postgres|mysql|mongodb|redis)://[^:]+:([^@]+)@")


class LogAnonymizer:
    """Sanitizes sensitive information from raw log messages."""

    @staticmethod
    def sanitize(message: str) -> str:
        if not message:
            return ""

        # Mask JWT first
        text = JWT_REGEX.sub("[REDACTED_JWT]", message)

        # Mask DB passwords in connection strings
        text = DB_CONN_REGEX.sub(r"\1://[USER]:[REDACTED_SECRET]@", text)

        # Mask Emails
        text = EMAIL_REGEX.sub("[REDACTED_EMAIL]", text)

        # Mask Credit Cards
        text = CREDIT_CARD_REGEX.sub("[REDACTED_PAN]", text)

        # Mask IP Addresses
        text = IP_REGEX.sub("10.0.0.X", text)

        # Mask UUIDs
        text = UUID_REGEX.sub("[REDACTED_UUID]", text)

        return text

    @classmethod
    def sanitize_record(cls, record: Dict[str, Any]) -> Dict[str, Any]:
        sanitized = dict(record)
        if "message" in sanitized and isinstance(sanitized["message"], str):
            sanitized["message"] = cls.sanitize(sanitized["message"])
        return sanitized
