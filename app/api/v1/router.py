"""
===============================================================================
API V1 Router
===============================================================================
"""

from fastapi import APIRouter

from app.api.v1.routes.system import router as system_router
from app.api.v1.routes.prediction import router as prediction_router

router = APIRouter()

router.include_router(system_router)
router.include_router(prediction_router)