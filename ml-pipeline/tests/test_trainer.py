import numpy as np

from app.models.model_factory import ModelFactory
from app.services.trainer import Trainer


def test_trainer():

    model = ModelFactory.create_model()

    trainer = Trainer(model)

    X = np.random.rand(100, 41)

    result = trainer.train(X)

    assert result.sample_count == 100

    assert result.feature_count == 41

    assert result.model is not None

    assert result.training_time_seconds >= 0