from datetime import datetime

from pydantic import BaseModel


class ModelInfoResponse(BaseModel):
    model_name: str
    model_version: str
    algorithm: str
    feature_version: str
    feature_count: int
    dataset_size: int
    contamination: float
    random_state: int
    python_version: str
    sklearn_version: str
    trained_at: datetime