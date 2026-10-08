from __future__ import annotations

from pathlib import Path

import pandas as pd

from app.data.dataset_validator import DatasetValidator


class JsonReader:

    @staticmethod
    def read(path: str | Path) -> pd.DataFrame:

        dataframe = pd.read_json(path)

        DatasetValidator.validate(dataframe)

        return dataframe