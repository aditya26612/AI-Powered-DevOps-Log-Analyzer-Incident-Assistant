import pandas as pd
import pytest

from app.data.dataset_validator import (
    DatasetValidationError,
    DatasetValidator,
)


def test_valid_dataset():

    df = pd.DataFrame(
        {
            "timestamp": ["2026-01-01T00:00:00"],
            "level": ["INFO"],
            "service_name": ["user-service"],
            "message": ["Application started"],
        }
    )

    DatasetValidator.validate(df)


def test_missing_column():

    df = pd.DataFrame(
        {
            "timestamp": ["2026-01-01"],
            "level": ["INFO"],
        }
    )

    with pytest.raises(DatasetValidationError):
        DatasetValidator.validate(df)