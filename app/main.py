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

    yield

    logger.info("Shutting down DevInsight ML Pipeline...")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan,
)
register_exception_handlers(app)
app.include_router(api_router)

