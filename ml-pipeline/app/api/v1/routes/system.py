"""
===============================================================================
DevInsight AI - System Routes
===============================================================================
"""

from fastapi import APIRouter, Depends

from app.bootstrap.system import SystemBootstrap
from app.services.system_service import SystemService

from app.api.v1.schemas.responses.health_response import HealthResponse
from app.api.v1.schemas.responses.model_info_response import (
    ModelInfoResponse,
)

router = APIRouter(
    prefix="/api/v1/system",
    tags=["System"],
)


def get_system_service() -> SystemService:
    """
    Dependency provider for SystemService.
    """
    return SystemBootstrap.create()


@router.get(
    "/health",
    response_model=HealthResponse,
)
def health(
    system_service: SystemService = Depends(get_system_service),
) -> HealthResponse:
    """
    Returns the operational health of the ML service.
    """
    return system_service.get_health()


@router.get(
    "/model",
    response_model=ModelInfoResponse,
)
def model_info(
    system_service: SystemService = Depends(get_system_service),
) -> ModelInfoResponse:
    """
    Returns metadata about the currently deployed model.
    """
    return system_service.get_model_info()