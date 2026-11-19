"""
===============================================================================
DevInsight AI - System Bootstrap
===============================================================================

Creates a fully configured SystemService.

This is the composition root for system-related operations such as
health checks and model metadata retrieval.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from pathlib import Path

from app.artifacts.artifact_manager import ArtifactManager
from app.core.config import get_settings
from app.services.system_service import SystemService


class SystemBootstrap:

    @staticmethod
    def create() -> SystemService:

        settings = get_settings()

        artifact_dir = Path(settings.MODEL_PATH).parent if settings.MODEL_PATH else Path("artifacts")
        artifact_manager = ArtifactManager(artifact_directory=artifact_dir)

        return SystemService(
            settings=settings,
            artifact_manager=artifact_manager,
        )
