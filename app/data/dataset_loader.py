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

        elif suffix == ".json":
            dataframe = JsonReader.read(path)

        else:
            raise ValueError(
                f"Unsupported dataset format: {suffix}"
            )

        logs: list[LogEntry] = []

        for row in dataframe.itertuples(index=False):

            logs.append(
                LogEntry(
                    timestamp=datetime.fromisoformat(
                        str(row.timestamp)
                    ),
                    level=str(row.level),
                    service_name=str(row.service_name),
                    message=str(row.message),
                )
            )

        return logs