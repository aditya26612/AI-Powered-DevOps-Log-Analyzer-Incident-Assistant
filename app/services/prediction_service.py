"""
===============================================================================
DevInsight AI - Prediction Service
===============================================================================

Business service responsible for performing inference using the trained
machine learning model.

Workflow:
    LogEntry
        ↓
    Feature Extraction
        ↓
    Feature Matrix
        ↓
    Feature Scaling
        ↓
    Model Prediction
        ↓
    Prediction Result
"""

from __future__ import annotations

from sklearn.base import BaseEstimator

from app.domain.log_entry import LogEntry
from app.domain.prediction_result import PredictionResult
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.domain.model_metadata import ModelMetadata




class PredictionService:
    """
    Performs inference on log entries using the trained model.
    """

    def __init__(
        self,
        model: BaseEstimator,
        preprocessor: PreprocessingService,
        feature_builder: FeatureMatrixBuilder,
        metadata: ModelMetadata,
    ) -> None:
        self._model = model
        self._preprocessor = preprocessor
        self._feature_builder = feature_builder
        self._metadata = metadata

    def predict(
            self,
            log: LogEntry,
        ) -> PredictionResult:
            """
            Predict a single log entry.
            """

            return self.predict_batch([log])[0]

    @property
    def metadata(self) -> ModelMetadata:
        """
        Returns metadata of the currently loaded model.
        """
        return self._metadata

    def predict_batch(
        self,
        logs: list[LogEntry],
        ) -> list[PredictionResult]:
        """
        Predict multiple log entries.
        """

        # ---------------------------------------------------------
        # Feature Extraction
        # ---------------------------------------------------------

        feature_matrix = self._feature_builder.build(logs)

        # ---------------------------------------------------------
        # Feature Scaling
        # ---------------------------------------------------------

        transformed = self._preprocessor.transform(
            feature_matrix
        )

        # ---------------------------------------------------------
        # Model Prediction
        # ---------------------------------------------------------

        predictions = self._model.predict(
            transformed
        )

        decision_scores = self._model.decision_function(
            transformed
        )

        # ---------------------------------------------------------
        # Build Results
        # ---------------------------------------------------------

        results = []

        for prediction, score in zip(
            predictions,
            decision_scores,
        ):

            prediction = int(prediction)
            score = float(score)

            results.append(
                PredictionResult(
                    prediction=prediction,
                    is_anomaly=(prediction == -1),
                    decision_score=score,
                )
            )

        return results