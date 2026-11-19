"""
===============================================================================
DevInsight AI - Dataset Loader
===============================================================================
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

from app.data.csv_reader import CsvReader
from app.domain.log_entry import LogEntry
from app.data.json_reader import JsonReader

class DatasetLoader:

    @staticmethod
    def load(path: str | Path) -> list[LogEntry]:

        suffix = Path(path).suffix.lower()

        if suffix == ".csv":
            dataframe = CsvReader.read(path)

        elif suffix in (".json", ".jsonl"):
            dataframe = JsonReader.read(path)

        else:
            raise ValueError(
                f"Unsupported dataset format: {suffix}"
            )

        logs: list[LogEntry] = []

        has_is_true_anomaly = "is_true_anomaly" in dataframe.columns
        has_is_anomaly = "is_anomaly" in dataframe.columns
        has_label = "label" in dataframe.columns or "ground_truth" in dataframe.columns
        label_col = "label" if "label" in dataframe.columns else ("ground_truth" if "ground_truth" in dataframe.columns else None)
        has_rationale = "label_rationale" in dataframe.columns
        has_log_id = "log_id" in dataframe.columns

        import numpy as np

        for row in dataframe.itertuples(index=False):
            is_anomaly_val = None
            if has_is_anomaly:
                raw_val = getattr(row, "is_anomaly")
                if raw_val is not None and not (isinstance(raw_val, float) and np.isnan(raw_val)):
                    is_anomaly_val = bool(raw_val)
                else:
                    is_anomaly_val = None
            elif has_is_true_anomaly:
                raw_val = getattr(row, "is_true_anomaly")
                if raw_val is not None and not (isinstance(raw_val, float) and np.isnan(raw_val)):
                    is_anomaly_val = bool(raw_val)
                else:
                    is_anomaly_val = None

            label_val = str(getattr(row, label_col)) if label_col and getattr(row, label_col) is not None else None
            if label_val == "UNCERTAIN":
                is_anomaly_val = None

            rationale_val = str(getattr(row, "label_rationale")) if has_rationale and getattr(row, "label_rationale") is not None else None
            log_id_val = str(getattr(row, "log_id")) if has_log_id and getattr(row, "log_id") is not None else None

            logs.append(
                LogEntry(
                    timestamp=datetime.fromisoformat(
                        str(row.timestamp)
                    ),
                    level=str(row.level),
                    service_name=str(row.service_name),
                    message=str(row.message),
                    is_anomaly=is_anomaly_val,
                    label=label_val,
                    label_rationale=rationale_val,
                    log_id=log_id_val,
                )
            )

        return logs