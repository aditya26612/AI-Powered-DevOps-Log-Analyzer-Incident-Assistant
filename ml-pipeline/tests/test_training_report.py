from app.domain.training_report import TrainingReport


def test_training_report():

    report = TrainingReport(
        sample_count=1000,
        feature_count=41,
        anomaly_count=50,
        anomaly_ratio=0.05,
        training_time_seconds=2.75,
        artifacts_saved=True,
    )

    assert report.sample_count == 1000

    assert report.feature_count == 41

    assert report.anomaly_count == 50

    assert report.artifacts_saved is True