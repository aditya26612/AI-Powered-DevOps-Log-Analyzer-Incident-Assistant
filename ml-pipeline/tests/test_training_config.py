from pathlib import Path

from app.core.training_config import TrainingConfig


def test_training_config():

    config = TrainingConfig()

    assert config.dataset_path == Path("data/synthetic_logs.json")
    assert config.enable_scaling is True

    assert config.save_artifacts is True