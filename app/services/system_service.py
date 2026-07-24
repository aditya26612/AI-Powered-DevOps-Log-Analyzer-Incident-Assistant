"""
===============================================================================
DevInsight AI - System Service
===============================================================================

Provides operational information about the ML service.

Responsibilities
----------------
- Health monitoring
- Model metadata retrieval

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from app.artifacts.artifact_manager import ArtifactManager
from app.core.config import Settings
from app.domain.model_metadata import ModelMetadata
from app.api.v1.schemas.responses.health_response import HealthResponse
from app.api.v1.schemas.responses.model_info_response import ModelInfoResponse
from app.api.v1.schemas.responses.artifact_health_response import ArtifactHealthResponse

class SystemService:
    """
    Provides operational information about the ML service.
    """

    def __init__(
        self,
        settings: Settings,
        artifact_manager: ArtifactManager,
    ) -> None:

        self._settings = settings
        self._artifact_manager = artifact_manager

    # ==========================================================
    # Private Helpers
    # ==========================================================

    def _load_metadata(
        self,
    ) -> ModelMetadata:
        """
        Load model metadata from artifact storage.
        """

        return self._artifact_manager.load_metadata()

    def _down_health_response(
        self,
    ) -> HealthResponse:

        return HealthResponse(
            status="DOWN",
            service=self._settings.APP_NAME,
            version=self._settings.APP_VERSION,
            environment=self._settings.ENVIRONMENT,
            model_loaded=False,
            model_version="N/A",
            algorithm="N/A",
            artifacts=ArtifactHealthResponse(
                model=self._artifact_manager.model_exists(),
                scaler=self._artifact_manager.scaler_exists(),
                metadata=self._artifact_manager.metadata_exists(),
            ),
        )

    # ==========================================================
    # Public API
    # ==========================================================

    def get_health(
        self,
    ) -> HealthResponse:
        """
        Return overall service health.
        """

        # Artifacts not available
        if not self._artifact_manager.artifacts_exist():
            return self._down_health_response()

        try:
            metadata = self._load_metadata()

            return HealthResponse(
                status="UP",
                service=self._settings.APP_NAME,
                version=self._settings.APP_VERSION,
                environment=self._settings.ENVIRONMENT,
                model_loaded=True,
                model_version=metadata.model_version,
                algorithm=metadata.algorithm,
                artifacts=ArtifactHealthResponse(
                    model=True,
                    scaler=True,
                    metadata=True,
                ),
            )

        except Exception:
            # Metadata exists but cannot be loaded
            # (corrupted JSON, invalid schema, etc.)
            return self._down_health_response()

    def get_model_info(
        self,
    ) -> ModelInfoResponse:
        """
        Return trained model information.
        """

        metadata = self._load_metadata()

        return ModelInfoResponse(
            model_name=metadata.model_name,
            model_version=metadata.model_version,
            algorithm=metadata.algorithm,
            feature_version=metadata.feature_version,
            feature_count=metadata.feature_count,
            dataset_size=metadata.dataset_size,
            contamination=metadata.contamination,
            random_state=metadata.random_state,
            python_version=metadata.python_version,
            sklearn_version=metadata.sklearn_version,
            trained_at=metadata.trained_at,
        )