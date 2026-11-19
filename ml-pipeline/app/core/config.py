from functools import lru_cache
from pathlib import Path
from typing import Any, Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    Application configuration loaded from environment variables.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ------------------------------------------------------------------
    # Application
    # ------------------------------------------------------------------

    APP_NAME: str = "DevInsight ML Pipeline"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: str = "development"
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # ------------------------------------------------------------------
    # Logging
    # ------------------------------------------------------------------

    LOG_LEVEL: str = "INFO"

    # ------------------------------------------------------------------
    # ML Architecture & Model Selection
    # ------------------------------------------------------------------

    ACTIVE_MODEL: Literal["v1", "v2"] = Field(
        default="v2",
        description="Active model architecture: 'v2' (CuratedIsolationForest) or 'v1' (Legacy IsolationForest)",
    )

    MODEL_PATH: str = Field(
        default="",
        description="Path to primary model artifact. If empty, auto-resolved from ACTIVE_MODEL.",
    )

    ANOMALY_THRESHOLD: float | None = Field(
        default=None,
        ge=-1.0,
        le=1000.0,
        description="Decision threshold for anomaly classification. If None, auto-resolved from ACTIVE_MODEL.",
    )

    SHADOW_MODE_ENABLED: bool = Field(
        default=False,
        description="Enable safe shadow mode evaluation of candidate models",
    )

    CANDIDATE_MODEL_PATH: str = "artifacts/candidates/v2_curated_isolation_forest/model.pkl"
    CANDIDATE_METADATA_PATH: str = "artifacts/candidates/v2_curated_isolation_forest/metadata.json"
    CANDIDATE_THRESHOLD: float = -0.02

    # ------------------------------------------------------------------
    # Internal Security
    # ------------------------------------------------------------------

    INTERNAL_API_KEY: str = Field(
        default="",
        description="Shared internal API key for microservice-to-microservice authentication",
    )

    def model_post_init(self, __context: Any) -> None:
        if not self.MODEL_PATH:
            if self.ACTIVE_MODEL.lower() == "v2":
                self.MODEL_PATH = "artifacts/candidates/v2_curated_isolation_forest/model.pkl"
            else:
                self.MODEL_PATH = "artifacts/model.pkl"

        if self.ANOMALY_THRESHOLD is None or self.ANOMALY_THRESHOLD == 999.0:
            if self.ACTIVE_MODEL.lower() == "v2":
                self.ANOMALY_THRESHOLD = -0.02
            else:
                self.ANOMALY_THRESHOLD = 0.0

        # Path resolution fallback when executing from repository root
        if not Path(self.MODEL_PATH).exists() and (Path("ml-pipeline") / self.MODEL_PATH).exists():
            self.MODEL_PATH = str(Path("ml-pipeline") / self.MODEL_PATH)

        if not Path(self.CANDIDATE_MODEL_PATH).exists() and (Path("ml-pipeline") / self.CANDIDATE_MODEL_PATH).exists():
            self.CANDIDATE_MODEL_PATH = str(Path("ml-pipeline") / self.CANDIDATE_MODEL_PATH)

        if not Path(self.CANDIDATE_METADATA_PATH).exists() and (Path("ml-pipeline") / self.CANDIDATE_METADATA_PATH).exists():
            self.CANDIDATE_METADATA_PATH = str(Path("ml-pipeline") / self.CANDIDATE_METADATA_PATH)



@lru_cache
def get_settings() -> Settings:
    """
    Returns a singleton Settings instance.
    """
    return Settings()


settings = get_settings()