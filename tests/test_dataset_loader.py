from pathlib import Path

from app.data.dataset_loader import DatasetLoader


def test_load_csv_dataset():

    dataset = DatasetLoader.load(
        Path("tests/data/sample_logs.csv")
    )

    assert len(dataset) == 2
    assert dataset[0].service_name == "user-service"


def test_load_json_dataset():

    dataset = DatasetLoader.load(
        Path("data/synthetic_logs.json")
    )

    assert len(dataset) == 10000