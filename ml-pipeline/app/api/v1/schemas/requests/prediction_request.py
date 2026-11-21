from datetime import datetime, timezone

from pydantic import BaseModel, Field, field_validator


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
        strict=True,
        min_length=1,
        max_length=20,
        description="Log level",
    )

    service_name: str = Field(
        ...,
        strict=True,
        min_length=1,
        max_length=100,
        description="Application or service name",
    )

    message: str = Field(
        ...,
        strict=True,
        min_length=1,
        max_length=16_384,
        description="Log message",
    )

    @field_validator("timestamp", mode="before")
    @classmethod
    def require_iso_text(cls, value):
        if not isinstance(value, str) or "T" not in value and " " not in value or len(value) > 40:
            raise ValueError("Use an ISO timestamp")
        return value

    @field_validator("timestamp")
    @classmethod
    def normalize_utc(cls, value: datetime) -> datetime:
        try:
            return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value.astimezone(timezone.utc)
        except OverflowError as exc:
            raise ValueError("Timestamp out of range") from exc

    @field_validator("level", "service_name", "message")
    @classmethod
    def validate_text(cls, value: str) -> str:
        if not value.strip() or "\x00" in value:
            raise ValueError("Invalid text")
        value.encode("utf-8")
        return value
