from pydantic import BaseModel

from app.api.v1.schemas.responses.artifact_health_response import (
    ArtifactHealthResponse,
)


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    environment: str

    model_loaded: bool
    model_version: str
    algorithm: str

    artifacts: ArtifactHealthResponse