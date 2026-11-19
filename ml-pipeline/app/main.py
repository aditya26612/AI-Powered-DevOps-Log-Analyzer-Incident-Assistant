# Compatibility shim: modern Starlette versions removed on_startup/on_shutdown from Router
import starlette.routing

_orig_router_init = starlette.routing.Router.__init__


def _patched_router_init(self, *args, **kwargs):
    kwargs.pop("on_startup", None)
    kwargs.pop("on_shutdown", None)
    self.on_startup = []
    self.on_shutdown = []
    return _orig_router_init(self, *args, **kwargs)


starlette.routing.Router.__init__ = _patched_router_init

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.v1.router import router as api_router
from app.core.config import settings
from app.core.logging import configure_logging, get_logger

from app.exceptions.handlers import register_exception_handlers

configure_logging(settings.LOG_LEVEL)
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application startup and shutdown events.
    """

    logger.info("Starting DevInsight ML Pipeline...")

    from app.api.v1.dependencies import get_prediction_service
    service = get_prediction_service()
    try:
        yield
    finally:
        service.close()
        get_prediction_service.cache_clear()

    logger.info("Shutting down DevInsight ML Pipeline...")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan,
)
register_exception_handlers(app)
app.include_router(api_router)
