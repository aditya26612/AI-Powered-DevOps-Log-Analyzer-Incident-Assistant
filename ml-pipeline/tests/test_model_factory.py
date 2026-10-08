from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from app.models.model_factory import ModelFactory


def test_create_model():

    model = ModelFactory.create_model()

    assert isinstance(model, IsolationForest)


def test_create_scaler():

    scaler = ModelFactory.create_scaler()

    assert isinstance(scaler, StandardScaler)