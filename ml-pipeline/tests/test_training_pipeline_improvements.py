from pathlib import Path
from app.core.training_config import TrainingConfig
from app.bootstrap.training import TrainingBootstrap


def test_training_pipeline_template_grouped_split():
    config = TrainingConfig(
        dataset_path=Path("data/labeled_operational_logs.jsonl"),
        save_artifacts=False,
        split_strategy="template_grouped",
        test_size=0.20,
        n_estimators=50,
        contamination=0.08,
        random_state=42
    )

    pipeline = TrainingBootstrap.create(config)
    report = pipeline.run()

    assert report.sample_count > 0
    assert report.test_sample_count is not None
    assert report.test_sample_count > 0
    assert report.f1_score is not None
    assert report.precision is not None
    assert report.recall is not None
    assert report.confusion_matrix is not None
    assert "tp" in report.confusion_matrix


def test_training_config_propagation():
    config = TrainingConfig(
        dataset_path=Path("data/labeled_operational_logs.jsonl"),
        save_artifacts=False,
        n_estimators=75,
        contamination=0.12,
        random_state=99
    )

    pipeline = TrainingBootstrap.create(config)
    model = pipeline._trainer.model

    assert model.n_estimators == 75
    assert model.contamination == 0.12
    assert model.random_state == 99
