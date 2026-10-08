"""
===============================================================================
DevInsight AI - Training Bootstrap
===============================================================================

Creates a fully configured TrainingPipeline.

This is the composition root for the offline training system.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""
from app.models.model_factory import ModelFactory

from app.artifacts.artifact_manager import ArtifactManager
from app.core.training_config import TrainingConfig
from app.data.dataset_loader import DatasetLoader
from app.features.orchestrator import FeatureOrchestrator
from app.models.model_factory import ModelFactory
from app.services.evaluator import Evaluator
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.services.trainer import Trainer
from app.training.pipeline import TrainingPipeline


class TrainingBootstrap:

    @staticmethod
    def create(
        config: TrainingConfig | None = None,
    ) -> TrainingPipeline:

        config = config or TrainingConfig()

        orchestrator = FeatureOrchestrator()

        dataset_loader = DatasetLoader()

        feature_builder = FeatureMatrixBuilder(
            orchestrator
        )

        scaler = ModelFactory.create_scaler()

        preprocessor = PreprocessingService(scaler)
        trainer = Trainer(
            ModelFactory.create_model()
        )

        evaluator = Evaluator()

        artifact_manager = ArtifactManager()

        return TrainingPipeline(
            dataset_loader=dataset_loader,
            feature_builder=feature_builder,
            preprocessor=preprocessor,
            trainer=trainer,
            evaluator=evaluator,
            artifact_manager=artifact_manager,
            config=config,
        )