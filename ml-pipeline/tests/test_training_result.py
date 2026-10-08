from app.domain.training_result import TrainingResult
from app.models.model_factory import ModelFactory


def test_training_result():

    model = ModelFactory.create_model()

    result = TrainingResult(
        model=model,
        feature_count=41,
        sample_count=100,
        training_time_seconds=1.23,
    )

    assert result.feature_count == 41

    assert result.sample_count == 100

    assert result.training_time_seconds == 1.23

    assert result.model is model