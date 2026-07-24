from datetime import UTC, datetime
from http import HTTPStatus

from fastapi import FastAPI
from fastapi import HTTPException
from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette import status

from app.exceptions.custom_exceptions import (
    FeatureExtractionException,
    InvalidPredictionException,
    ModelNotLoadedException,
    PredictionException,
)
from app.exceptions.error_response import ErrorResponse


def register_exception_handlers(app: FastAPI) -> None:
    """
    Register all global exception handlers.
    """

    @app.exception_handler(ModelNotLoadedException)
    async def model_not_loaded_handler(
        request: Request,
        exc: ModelNotLoadedException,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            error=HTTPStatus(status.HTTP_503_SERVICE_UNAVAILABLE).phrase,
            message=str(exc),
        )

    @app.exception_handler(FeatureExtractionException)
    async def feature_extraction_handler(
        request: Request,
        exc: FeatureExtractionException,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_400_BAD_REQUEST,
            error=HTTPStatus(status.HTTP_400_BAD_REQUEST).phrase,
            message=str(exc),
        )

    @app.exception_handler(InvalidPredictionException)
    async def invalid_prediction_handler(
        request: Request,
        exc: InvalidPredictionException,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            error=HTTPStatus(status.HTTP_422_UNPROCESSABLE_CONTENT).phrase,
            message=str(exc),
        )

    @app.exception_handler(PredictionException)
    async def prediction_exception_handler(
        request: Request,
        exc: PredictionException,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            error=HTTPStatus(status.HTTP_500_INTERNAL_SERVER_ERROR).phrase,
            message=str(exc),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request,
        _: RequestValidationError,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            error=HTTPStatus(status.HTTP_422_UNPROCESSABLE_CONTENT).phrase,
            message="Request validation failed.",
        )

    @app.exception_handler(HTTPException)
    async def http_exception_handler(
        request: Request,
        exc: HTTPException,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=exc.status_code,
            error=HTTPStatus(exc.status_code).phrase,
            message=str(exc.detail),
        )

    @app.exception_handler(Exception)
    async def generic_exception_handler(
        request: Request,
        _: Exception,
    ) -> JSONResponse:
        return _build_response(
            request=request,
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            error=HTTPStatus(status.HTTP_500_INTERNAL_SERVER_ERROR).phrase,
            message="An unexpected error occurred.",
        )


def _build_response(
    request: Request,
    status_code: int,
    error: str,
    message: str,
) -> JSONResponse:
    """
    Build and return a standardized error response.
    """

    response = ErrorResponse(
        timestamp=datetime.now(UTC),
        status=status_code,
        error=error,
        message=message,
        path=request.url.path,
    )

    return JSONResponse(
        status_code=status_code,
        content=response.model_dump(mode="json"),
    )