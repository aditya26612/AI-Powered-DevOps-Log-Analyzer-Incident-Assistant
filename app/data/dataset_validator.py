"""
===============================================================================
DevInsight AI - Dataset Validator
===============================================================================

Validates datasets before training.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import pandas as pd


class DatasetValidationError(ValueError):
    """
    Raised when a dataset is invalid.
    """


class DatasetValidator:

    REQUIRED_COLUMNS = {
        "timestamp",
        "level",
        "service_name",
        "message",
    }

    @classmethod
    def validate(
        cls,
        dataframe: pd.DataFrame,
    ) -> None:

        missing = cls.REQUIRED_COLUMNS - set(dataframe.columns)

        if missing:
            raise DatasetValidationError(
                f"Missing required columns: {sorted(missing)}"
            )

        if dataframe.empty:
            raise DatasetValidationError(
                "Dataset is empty."
            )