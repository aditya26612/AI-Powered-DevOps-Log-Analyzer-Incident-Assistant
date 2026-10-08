from datetime import datetime

from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    """
    Incoming log request for anomaly prediction.
    """

    timestamp: datetime = Field(
        ...,
        description="Timestamp of the log event",
    )

    level: str = Field(
        ...,
        min_length=1,
        max_length=20,
        description="Log level",
    )

    service_name: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description="Application or service name",
    )

    message: str = Field(
        ...,
        min_length=1,
        description="Log message",
    )