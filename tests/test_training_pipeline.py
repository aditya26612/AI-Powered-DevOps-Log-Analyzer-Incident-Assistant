from pathlib import Path

from app.core.training_config import TrainingConfig
from app.training.pipeline import TrainingPipeline

from app.bootstrap.training import TrainingBootstrap

def test_training_pipeline():

    config = TrainingConfig(
        dataset_path=Path("tests/data/sample_logs.csv"),
        save_artifacts=False,
    )

    pipeline = TrainingBootstrap.create(config)

    report = pipeline.run()

    assert report.sample_count > 0

    assert report.feature_count > 0

    assert report.training_time_seconds >= 0