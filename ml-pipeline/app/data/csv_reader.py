"""
===============================================================================
DevInsight AI - CSV Reader
===============================================================================
"""

from __future__ import annotations

from pathlib import Path

import pandas as pd

from app.data.dataset_validator import DatasetValidator


class CsvReader:

    @staticmethod
    def read(path: str | Path) -> pd.DataFrame:

        # Convert to absolute path
        path = Path(path).resolve()

        # Read CSV
        dataframe = pd.read_csv(path)

        # Validate dataset
        DatasetValidator.validate(dataframe)

        return dataframe