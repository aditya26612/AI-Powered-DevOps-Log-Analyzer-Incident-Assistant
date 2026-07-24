import numpy as np

from app.domain.training_result import TrainingResult
from app.models.model_factory import ModelFactory
from app.services.evaluator import Evaluator


def test_evaluator():

    model = ModelFactory.create_model()

    result = TrainingResult(
        model=model,
        feature_count=41,
        sample_count=100,
        training_time_seconds=1.5,
    )

    predictions = np.array(
        [
            1,
            1,
            -1,
            1,
            -1,
        ]
    )

    report = Evaluator.evaluate(
        result=result,
        predictions=predictions,
        artifacts_saved=True,
    )

    assert report.sample_count == 100

    assert report.feature_count == 41

    assert report.anomaly_count == 2

    assert report.anomaly_ratio == 0.4

    assert report.artifacts_saved is True