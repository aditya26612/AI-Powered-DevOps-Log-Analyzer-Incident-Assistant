import numpy as np

from app.domain.feature_vector import FeatureVector
from app.features.feature_names import FEATURE_NAMES
from app.services.matrix_builder import MatrixBuilder


def test_matrix_builder():

    vectors = [

        FeatureVector(
            values=[1.0, 2.0],
            feature_names=FEATURE_NAMES[:2],
        ),

        FeatureVector(
            values=[3.0, 4.0],
            feature_names=FEATURE_NAMES[:2],
        ),
    ]

    X = MatrixBuilder.build(vectors)

    assert isinstance(X, np.ndarray)

    assert X.shape == (2, 2)