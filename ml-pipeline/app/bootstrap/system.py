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

from app.artifacts.artifact_manager import ArtifactManager
from app.core.config import Settings
from app.services.system_service import SystemService


class SystemBootstrap:

    @staticmethod
    def create() -> SystemService:

        settings = Settings()

        artifact_manager = ArtifactManager()

        return SystemService(
            settings=settings,
            artifact_manager=artifact_manager,
        )