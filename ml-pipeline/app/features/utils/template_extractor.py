"""
===============================================================================
DevInsight AI - Log Template Extractor
===============================================================================
Normalizes log messages by masking dynamic parameters (numbers, UUIDs, IPs, hashes)
to identify underlying log event templates.
Used for template-aware grouped train/validation/test splitting to prevent
synthetic template leakage across partitions.
===============================================================================
"""

import re


_IP_RE = re.compile(r"\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b")
_UUID_RE = re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
_HEX_RE = re.compile(r"\b0x[0-9a-fA-F]+\b|\b[0-9a-fA-F]{16,}\b")
_NUM_RE = re.compile(r"\b\d+\b")
_QUOTED_RE = re.compile(r"'[^']*'|\"[^\"]*\"")


class TemplateExtractor:
    """Extracts structural message templates from log lines."""

    @staticmethod
    def extract_template(message: str) -> str:
        if not message or not message.strip():
            return "<EMPTY>"

        t = _IP_RE.sub("<IP>", message)
        t = _UUID_RE.sub("<UUID>", t)
        t = _HEX_RE.sub("<HEX>", t)
        t = _QUOTED_RE.sub("<STR>", t)
        t = _NUM_RE.sub("<NUM>", t)
        # Collapse multiple spaces
        t = re.sub(r"\s+", " ", t).strip()
        return t
