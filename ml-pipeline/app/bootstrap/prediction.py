"""
===============================================================================
DevInsight AI - Prediction Bootstrap
===============================================================================
"""

from __future__ import annotations

from app.artifacts.artifact_manager import ArtifactManager
from app.features.orchestrator import FeatureOrchestrator
from app.models.model_factory import ModelFactory
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.services.prediction_service import PredictionService


class PredictionBootstrap:

    @staticmethod
    def create() -> PredictionService:

        artifact_manager = ArtifactManager()

        model = artifact_manager.load_model()

        scaler = artifact_manager.load_scaler()

        metadata = artifact_manager.load_metadata()

        orchestrator = FeatureOrchestrator()

        feature_builder = FeatureMatrixBuilder(
            orchestrator
        )

        preprocessor = PreprocessingService(
            scaler
        )

        return PredictionService(
            model=model,
            preprocessor=preprocessor,
            feature_builder=feature_builder,
            metadata=metadata,
        )