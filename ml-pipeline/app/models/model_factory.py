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
    def create_model(
        n_estimators: int = N_ESTIMATORS,
        contamination: float = CONTAMINATION,
        max_samples: int | float | str = MAX_SAMPLES,
        max_features: float = MAX_FEATURES,
        bootstrap: bool = BOOTSTRAP,
        random_state: int = RANDOM_STATE,
        n_jobs: int = N_JOBS,
    ) -> IsolationForest:

        return IsolationForest(
            n_estimators=n_estimators,
            contamination=contamination,
            max_samples=max_samples,
            max_features=max_features,
            bootstrap=bootstrap,
            random_state=random_state,
            n_jobs=n_jobs,
        )

    @staticmethod
    def create_scaler(enable_scaling: bool = USE_STANDARD_SCALER):

        if enable_scaling:
            return StandardScaler()

        return None