"""
===============================================================================
DevInsight AI - Model Factory
===============================================================================
"""

from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from app.models.model_config import (
    BOOTSTRAP,
    CONTAMINATION,
    MAX_FEATURES,
    MAX_SAMPLES,
    N_ESTIMATORS,
    N_JOBS,
    RANDOM_STATE,
    USE_STANDARD_SCALER,
)


class ModelFactory:
    """
    Factory for ML models.
    """

    @staticmethod
    def create_model() -> IsolationForest:

        return IsolationForest(
            n_estimators=N_ESTIMATORS,
            contamination=CONTAMINATION,
            max_samples=MAX_SAMPLES,
            max_features=MAX_FEATURES,
            bootstrap=BOOTSTRAP,
            random_state=RANDOM_STATE,
            n_jobs=N_JOBS,
        )

    @staticmethod
    def create_scaler():

        if USE_STANDARD_SCALER:
            return StandardScaler()

        return None