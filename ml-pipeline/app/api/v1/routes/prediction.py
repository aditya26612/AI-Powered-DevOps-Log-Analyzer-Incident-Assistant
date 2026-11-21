from contextlib import asynccontextmanager
from threading import BoundedSemaphore

from fastapi import APIRouter, Depends, HTTPException
from starlette.concurrency import run_in_threadpool

from app.api.v1.dependencies import get_prediction_service, verify_internal_api_key
from app.api.v1.schemas.requests.prediction_request import PredictionRequest
from app.api.v1.schemas.requests.batch_prediction_request import BatchPredictionRequest
from app.api.v1.schemas.responses.prediction_response import PredictionResponse
from app.api.v1.schemas.responses.batch_prediction_response import BatchPredictionResponse

from app.core.constants import PREDICTION_LABELS

from app.domain.log_entry import LogEntry
from app.services.prediction_service import PredictionService


router = APIRouter(
    prefix="/api/v1",
    tags=["Prediction"],
    dependencies=[Depends(verify_internal_api_key)],
)

_slots = BoundedSemaphore(2)


@asynccontextmanager
async def inference_slot():
    if not _slots.acquire(blocking=False):
        raise HTTPException(status_code=503, detail="Inference at capacity")
    try:
        yield
    finally:
        _slots.release()


@router.post(
    "/predict",
    response_model=PredictionResponse,
)
async def predict(
    request: PredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
) -> PredictionResponse:

    log = LogEntry(
        timestamp=request.timestamp,
        level=request.level,
        service_name=request.service_name,
        message=request.message,
    )

    async with inference_slot():
        result = await run_in_threadpool(service.predict, log)

    return PredictionResponse(
        prediction=result.prediction,
        prediction_label=PREDICTION_LABELS[result.prediction],
        is_anomaly=result.is_anomaly,
        decision_score=result.decision_score,
        model_version=service.metadata.model_version,
    )


@router.post(
    "/predict/batch",
    response_model=BatchPredictionResponse,
)
async def predict_batch(
    request: BatchPredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
) -> BatchPredictionResponse:

    logs = [
        LogEntry(
            timestamp=log.timestamp,
            level=log.level,
            service_name=log.service_name,
            message=log.message,
        )
        for log in request.logs
    ]

    async with inference_slot():
        results = await run_in_threadpool(service.predict_batch, logs)

    predictions = [
        PredictionResponse(
            prediction=result.prediction,
            prediction_label=PREDICTION_LABELS[result.prediction],
            is_anomaly=result.is_anomaly,
            decision_score=result.decision_score,
            model_version=service.metadata.model_version,
        )
        for result in results
    ]

    return BatchPredictionResponse(
        predictions=predictions,
    )
