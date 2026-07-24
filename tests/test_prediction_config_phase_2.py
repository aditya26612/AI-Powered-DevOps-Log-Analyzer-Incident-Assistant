from pathlib import Path

from app.core.prediction_config import PredictionConfig


def test_prediction_config():

    config = PredictionConfig()

    assert config.artifact_directory == Path("artifacts")

    assert config.enable_scaling is True

    assert config.batch_size == 100