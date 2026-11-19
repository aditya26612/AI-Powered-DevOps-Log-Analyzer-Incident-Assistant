from __future__ import annotations

from pathlib import Path

import pandas as pd

from app.data.dataset_validator import DatasetValidator


class JsonReader:

    @staticmethod
    def read(path: str | Path) -> pd.DataFrame:
        path_str = str(path)
        if path_str.endswith(".jsonl"):
            dataframe = pd.read_json(path, lines=True)
        else:
            try:
                dataframe = pd.read_json(path)
            except ValueError:
                dataframe = pd.read_json(path, lines=True)

        DatasetValidator.validate(dataframe)

        return dataframe