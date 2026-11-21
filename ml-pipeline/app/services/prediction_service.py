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

import logging
import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import BoundedSemaphore
from datetime import datetime, timezone
from typing import Any
import numpy as np
from sklearn.base import BaseEstimator

from app.domain.log_entry import LogEntry
from app.domain.prediction_result import PredictionResult
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.domain.model_metadata import ModelMetadata




logger = logging.getLogger(__name__)


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
        threshold: float = 0.0,
        shadow_model: BaseEstimator | None = None,
        shadow_feature_builder: FeatureMatrixBuilder | None = None,
        shadow_threshold: float = -0.02,
        shadow_metadata: ModelMetadata | None = None,
        shadow_recorder: Any | None = None,
        background_shadow: bool = False,
    ) -> None:
        # Validate primary model / feature builder feature count compatibility
        model_n_in = getattr(model, "n_features_in_", None)
        if (
            isinstance(model_n_in, int)
            and hasattr(feature_builder, "_orchestrator")
            and hasattr(feature_builder._orchestrator, "feature_names")
        ):
            expected = len(feature_builder._orchestrator.feature_names)
            if model_n_in != expected:
                raise ValueError(
                    f"PredictionService feature mismatch: model expects {model_n_in} features, "
                    f"but feature builder provides {expected} features."
                )

        # Validate primary model / scaler compatibility
        if (
            preprocessor is not None
            and preprocessor.scaler is not None
        ):
            scaler_n_in = getattr(preprocessor.scaler, "n_features_in_", None)
            if isinstance(scaler_n_in, int) and isinstance(model_n_in, int):
                if scaler_n_in != model_n_in:
                    raise ValueError(
                        f"PredictionService model/scaler mismatch: model expects {model_n_in} features, "
                        f"but scaler was fitted with {scaler_n_in} features."
                    )

        # Validate shadow model feature compatibility; disable cleanly if mismatched
        if shadow_model is not None and shadow_feature_builder is not None:
            sh_model_n_in = getattr(shadow_model, "n_features_in_", None)
            if (
                isinstance(sh_model_n_in, int)
                and hasattr(shadow_feature_builder, "_orchestrator")
                and hasattr(shadow_feature_builder._orchestrator, "feature_names")
            ):
                sh_expected = len(shadow_feature_builder._orchestrator.feature_names)
                if sh_model_n_in != sh_expected:
                    logger.warning(
                        "Shadow model feature mismatch: shadow expects %d, builder provides %d. Disabling shadow mode.",
                        sh_model_n_in, sh_expected
                    )
                    shadow_model = None
                    shadow_feature_builder = None

        self._executor = ThreadPoolExecutor(max_workers=1) if background_shadow and shadow_model is not None else None
        self._slot = BoundedSemaphore(1)
        self._model = model
        self._preprocessor = preprocessor
        self._feature_builder = feature_builder
        self._metadata = metadata
        self._threshold = threshold
        self._shadow_model = shadow_model
        self._shadow_feature_builder = shadow_feature_builder
        self._shadow_threshold = shadow_threshold
        self._shadow_metadata = shadow_metadata
        self._shadow_recorder = shadow_recorder

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

    @property
    def threshold(self) -> float:
        """
        Returns the calibrated decision threshold used for predictions.
        """
        return self._threshold

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

        decision_scores = np.asarray(self._model.decision_function(
            transformed
        ))
        if (decision_scores.shape != (len(logs),)
                or not np.isfinite(decision_scores).all()
                or (np.abs(decision_scores) > 1.0).any()):
            raise ValueError("Invalid model decision scores")

        predictions = [
            -1 if float(sc) < self._threshold else 1
            for sc in decision_scores
        ]

        # ---------------------------------------------------------
        # Optional Shadow Mode Prediction
        # ---------------------------------------------------------
        if self._executor is not None:
            # At most one audit batch in flight per process. Drop comparisons under load.
            if len(logs) <= 256 and self._slot.acquire(blocking=False):
                try:
                    future = self._executor.submit(self._compare, logs, predictions, decision_scores)
                    future.add_done_callback(lambda _: self._slot.release())
                except RuntimeError:
                    self._slot.release()
            return [PredictionResult(prediction=p, is_anomaly=p == -1, decision_score=float(s))
                    for p, s in zip(predictions, decision_scores)]
        return self._compare(logs, predictions, decision_scores)

    def close(self):
        if self._executor is not None:
            self._executor.shutdown(wait=True, cancel_futures=True)

    def _compare(self, logs, predictions, decision_scores):
        shadow_preds = [None] * len(logs)
        shadow_scores = [None] * len(logs)
        if self._shadow_model is not None and self._shadow_feature_builder is not None:
            try:
                sh_mat = self._shadow_feature_builder.build(logs)
                sh_sc = self._shadow_model.decision_function(sh_mat)
                for i_s, sc in enumerate(sh_sc):
                    sc_fl = float(sc)
                    shadow_scores[i_s] = sc_fl
                    shadow_preds[i_s] = -1 if sc_fl < self._shadow_threshold else 1
            except Exception:
                pass

        # ---------------------------------------------------------
        # Build Results
        # ---------------------------------------------------------

        results = []

        for idx, (prediction, score) in enumerate(zip(
            predictions,
            decision_scores,
        )):

            prediction = int(prediction)
            score = float(score)
            sh_pred = shadow_preds[idx]
            sh_score = shadow_scores[idx]

            if sh_pred is not None:
                if sh_pred != prediction:
                    logger.warning(
                        "Shadow Mode DISAGREEMENT: primary_pred=%d (score=%.4f), "
                        "candidate_pred=%d (score=%.4f)",
                        prediction, score, sh_pred, sh_score,
                    )
                else:
                    logger.debug(
                        "Shadow Mode AGREEMENT: pred=%d (primary_score=%.4f, candidate_score=%.4f)",
                        prediction, score, sh_score
                    )

                if self._shadow_recorder is not None:
                    try:
                        ev_id = str(uuid.uuid4()) if self._executor else (logs[idx].log_id or str(uuid.uuid4()))
                        ev_ts = logs[idx].timestamp.isoformat() if logs[idx].timestamp else datetime.now(timezone.utc).isoformat()
                        p_ver = self._metadata.model_version if self._metadata else "1.0.0"
                        s_ver = self._shadow_metadata.model_version if self._shadow_metadata else "2.0.0-candidate"
                        self._shadow_recorder.record(
                            event_id=ev_id,
                            timestamp=ev_ts,
                            service_name="redacted" if self._executor else (logs[idx].service_name or "unknown"),
                            primary_model_version=p_ver,
                            primary_prediction=prediction,
                            primary_score=score,
                            shadow_model_version=s_ver,
                            shadow_prediction=sh_pred,
                            shadow_score=sh_score,
                            shadow_threshold=self._shadow_threshold,
                            is_disagreement=(sh_pred != prediction),
                            review_status="pending_review",
                        )
                    except Exception as s_rec_err:
                        logger.debug("Failed to record shadow audit: %s", s_rec_err)

            results.append(
                PredictionResult(
                    prediction=prediction,
                    is_anomaly=(prediction == -1),
                    decision_score=score,
                    shadow_prediction=sh_pred,
                    shadow_decision_score=sh_score,
                )
            )

        return results
