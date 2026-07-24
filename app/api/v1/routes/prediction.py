from fastapi import APIRouter, Depends

from app.api.v1.dependencies import get_prediction_service
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
)


@router.post(
    "/predict",
    response_model=PredictionResponse,
)
def predict(
    request: PredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
) -> PredictionResponse:

    log = LogEntry(
        timestamp=request.timestamp,
        level=request.level,
        service_name=request.service_name,
        message=request.message,
    )

    result = service.predict(log)

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
def predict_batch(
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

    results = service.predict_batch(logs)

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