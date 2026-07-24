import numpy as np

from app.models.model_factory import ModelFactory


from app.services.preprocessing_service import (
    PreprocessingService,
)


def test_fit_transform():


    scaler = ModelFactory.create_scaler()

    service = PreprocessingService(scaler)

    X = np.array(
        [
            [1.0, 2.0],
            [3.0, 4.0],
            [5.0, 6.0],
        ]
    )

    transformed = service.fit_transform(X)

    assert transformed.shape == X.shape

    assert service.scaler is not None


def test_transform():


    scaler = ModelFactory.create_scaler()

    service = PreprocessingService(scaler)
    X = np.array(
        [
            [1.0, 2.0],
            [3.0, 4.0],
        ]
    )

    service.fit_transform(X)

    transformed = service.transform(X)

    assert transformed.shape == X.shape