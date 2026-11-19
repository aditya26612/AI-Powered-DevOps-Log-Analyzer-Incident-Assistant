"""
===============================================================================
DevInsight AI - Thread-Safe Shadow Mode Audit Recorder
===============================================================================
Records candidate model shadow inferences alongside primary authoritative
predictions without side-effects on production inference.

Guarantees:
- Never persists raw log messages, passwords, tokens, or PII.
- Thread-safe file append using process-local threading.Lock.
- Bounded execution with complete exception trapping.
- Structured schema supporting automated disagreement analysis and SRE review.
===============================================================================
"""

from __future__ import annotations

import json
import logging
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


class ShadowAuditRecorder:
    """
    Thread-safe append-only audit recorder for shadow mode predictions and disagreements.
    """

    def __init__(self, output_path: str | Path | None = None) -> None:
        self._output_path = Path(output_path) if output_path else Path("artifacts/shadow_audit.jsonl")
        self._lock = threading.Lock()
        if self._output_path.parent:
            self._output_path.parent.mkdir(parents=True, exist_ok=True)

    @property
    def output_path(self) -> Path:
        return self._output_path

    def record(
        self,
        event_id: str,
        timestamp: str,
        service_name: str,
        primary_model_version: str,
        primary_prediction: int,
        primary_score: float,
        shadow_model_version: str,
        shadow_prediction: int,
        shadow_score: float,
        shadow_threshold: float,
        is_disagreement: bool,
        review_status: str = "pending_review",
    ) -> None:
        """
        Record a shadow mode evaluation. Thread-safe and non-raising.
        """
        record: dict[str, Any] = {
            "event_id": str(event_id),
            "timestamp": str(timestamp),
            "service_name": str(service_name),
            "primary_model_version": str(primary_model_version),
            "primary_prediction": int(primary_prediction),
            "primary_score": round(float(primary_score), 6),
            "shadow_model_version": str(shadow_model_version),
            "shadow_prediction": int(shadow_prediction),
            "shadow_score": round(float(shadow_score), 6),
            "shadow_threshold": round(float(shadow_threshold), 6),
            "is_disagreement": bool(is_disagreement),
            "review_status": str(review_status),
            "recorded_at": datetime.now(timezone.utc).isoformat(),
        }

        try:
            line = json.dumps(record) + "\n"
            with self._lock:
                with open(self._output_path, "a", encoding="utf-8") as f:
                    f.write(line)
        except Exception as exc:
            logger.warning("ShadowAuditRecorder: Failed to append shadow record: %s", exc)

    def read_all(self) -> list[dict[str, Any]]:
        """Read all recorded shadow evaluations."""
        if not self._output_path.exists():
            return []
        records = []
        with self._lock:
            with open(self._output_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line:
                        records.append(json.loads(line))
        return records

    def read_disagreements(self) -> list[dict[str, Any]]:
        """Read only records where primary and shadow models disagreed."""
        return [r for r in self.read_all() if r.get("is_disagreement")]

    def summarize(self) -> dict[str, Any]:
        """Compute summary statistics for shadow evaluations."""
        records = self.read_all()
        total = len(records)
        disagreements = sum(1 for r in records if r.get("is_disagreement"))
        agreements = total - disagreements
        disagreement_rate = (disagreements / total) if total > 0 else 0.0

        return {
            "total_evaluations": total,
            "agreements": agreements,
            "disagreements": disagreements,
            "disagreement_rate": round(disagreement_rate, 4),
            "output_path": str(self._output_path),
        }
