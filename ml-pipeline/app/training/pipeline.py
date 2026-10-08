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

        # ==============================================================
        # Preprocessing
        # ==============================================================

        if self._config.enable_scaling:

            X = self._preprocessor.fit_transform(X)

        # ==============================================================
        # Training
        # ==============================================================

        result = self._trainer.train(X)

        # ==============================================================
        # Evaluation
        # ==============================================================

        predictions = result.model.predict(X)

        # ==============================================================
        # Save Artifacts
        # ==============================================================

        artifacts_saved = False

        if self._config.save_artifacts:

            self._artifact_manager.save_model(
                result.model
            )

            self._artifact_manager.save_scaler(
                self._preprocessor.scaler
            )

            metadata = ModelMetadata(
            model_name="IsolationForest",
            model_version="1.0.0",
            algorithm="IsolationForest",
            feature_version="1.0.0",
            feature_count=FEATURE_COUNT,
            feature_names=FEATURE_NAMES,
            dataset_size=len(logs),
            contamination=result.model.contamination,
            random_state=result.model.random_state,
            python_version=platform.python_version(),
            sklearn_version=sklearn.__version__,
            trained_at=datetime.now(UTC),
            )

            self._artifact_manager.save_metadata(metadata)
            
            artifacts_saved = True

        # ==============================================================
        # Final Report
        # ==============================================================

        return self._evaluator.evaluate(
            result=result,
            predictions=predictions,
            artifacts_saved=artifacts_saved,
        )