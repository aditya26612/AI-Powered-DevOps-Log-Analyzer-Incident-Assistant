"""
===============================================================================
DevInsight AI - Training Pipeline
===============================================================================

Coordinates the complete model training workflow.

Responsibilities
----------------
1. Load dataset
2. Build feature matrix
3. Preprocess features
4. Train model
5. Evaluate model
6. Persist artifacts
7. Return training report

This class contains orchestration only.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import numpy as np

from app.artifacts.artifact_manager import ArtifactManager
from app.core.training_config import TrainingConfig
from app.data.dataset_loader import DatasetLoader
from app.features.orchestrator import FeatureOrchestrator
from app.models.model_factory import ModelFactory
from app.services.evaluator import Evaluator
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.services.trainer import Trainer
from app.domain.training_report import TrainingReport

from datetime import UTC, datetime
import platform
import sklearn

from app.domain.model_metadata import ModelMetadata
from app.features.feature_names import FEATURE_COUNT, FEATURE_NAMES

# app/training/pipeline.py

from app.features.feature_names import (
    FEATURE_COUNT,
    FEATURE_NAMES,
)

class TrainingPipeline:

    def __init__(
        self,
        dataset_loader: DatasetLoader,
        feature_builder: FeatureMatrixBuilder,
        preprocessor: PreprocessingService,
        trainer: Trainer,
        evaluator: Evaluator,
        artifact_manager: ArtifactManager,
        config: TrainingConfig,
    ) -> None:

        self._dataset_loader = dataset_loader

        self._feature_builder = feature_builder

        self._preprocessor = preprocessor

        self._trainer = trainer

        self._evaluator = evaluator

        self._artifact_manager = artifact_manager

        self._config = config

    def run(self) -> TrainingReport:
        """
        Execute the complete training pipeline.
        """

        # ==============================================================
        # Load dataset
        # ==============================================================

        logs = self._dataset_loader.load(
            self._config.dataset_path
        )

        # ==============================================================
        # Feature Engineering
        # ==============================================================

        X = self._feature_builder.build(logs)

        # Extract targets if available
        has_labels = any(log.is_anomaly is not None for log in logs)
        y_all = np.array([1 if log.is_anomaly else 0 for log in logs]) if has_labels else None

        # ==============================================================
        # Data Splitting & Preprocessing
        # ==============================================================

        if len(logs) >= 10 and self._config.test_size > 0.0:
            from sklearn.model_selection import GroupShuffleSplit, train_test_split
            from app.features.utils.template_extractor import TemplateExtractor

            if self._config.split_strategy == "template_grouped":
                templates = [TemplateExtractor.extract_template(l.message) for l in logs]
                gss = GroupShuffleSplit(n_splits=1, test_size=self._config.test_size, random_state=self._config.random_state)
                train_idx, test_idx = next(gss.split(X, y_all, groups=templates))
                X_train, X_test = X[train_idx], X[test_idx]
                y_train = y_all[train_idx] if y_all is not None else None
                y_test = y_all[test_idx] if y_all is not None else None
            elif self._config.split_strategy == "stratified" and y_all is not None:
                X_train, X_test, y_train, y_test = train_test_split(
                    X, y_all, test_size=self._config.test_size, random_state=self._config.random_state, stratify=y_all
                )
            else:
                if y_all is not None:
                    X_train, X_test, y_train, y_test = train_test_split(
                        X, y_all, test_size=self._config.test_size, random_state=self._config.random_state
                    )
                else:
                    X_train, X_test = train_test_split(
                        X, test_size=self._config.test_size, random_state=self._config.random_state
                    )
                    y_train, y_test = None, None

            # Fit preprocessor ONLY on training set
            if self._config.enable_scaling and self._preprocessor.scaler is not None:
                X_train = self._preprocessor.fit_transform(X_train)
                X_test = self._preprocessor.transform(X_test)

            result = self._trainer.train(X_train)
            eval_target_y = y_test
            eval_features = X_test

        else:
            # Fallback for minimal sample sizes or test_size == 0
            if self._config.enable_scaling and self._preprocessor.scaler is not None:
                X = self._preprocessor.fit_transform(X)

            result = self._trainer.train(X)
            eval_target_y = y_all
            eval_features = X

        scores = result.model.decision_function(eval_features)
        eval_preds = np.where(scores < self._config.decision_threshold, -1, 1)

        # ==============================================================
        # Save Artifacts
        # ==============================================================

        artifacts_saved = False

        if self._config.save_artifacts:

            model_hash = self._artifact_manager.save_model(result.model)
            requires_scaler = self._config.enable_scaling and self._preprocessor.scaler is not None
            if requires_scaler:
                self._artifact_manager.save_scaler(
                    self._preprocessor.scaler
                )

            metadata = ModelMetadata(
                model_name=self._config.model_name,
                model_version=self._config.artifacts_version,
                algorithm=result.model.__class__.__name__,
                feature_version="2.0.0" if self._config.feature_set == "curated" else "1.0.0",
                feature_count=result.feature_count,
                feature_names=getattr(self._feature_builder._orchestrator, "feature_names", FEATURE_NAMES),
                dataset_size=len(logs),
                contamination=getattr(result.model, "contamination", self._config.contamination),
                random_state=getattr(result.model, "random_state", self._config.random_state),
                python_version=platform.python_version(),
                sklearn_version=sklearn.__version__,
                trained_at=datetime.now(UTC),
                requires_scaler=requires_scaler,
                calibrated_decision_threshold=self._config.decision_threshold,
                sha256_model=model_hash,
            )

            self._artifact_manager.save_metadata(metadata)
            artifacts_saved = True

        # ==============================================================
        # Final Report
        # ==============================================================

        return self._evaluator.evaluate(
            result=result,
            predictions=eval_preds,
            artifacts_saved=artifacts_saved,
            y_true=eval_target_y,
            decision_scores=-scores,
        )
