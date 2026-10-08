from functools import lru_cache

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
    # ML
    # ------------------------------------------------------------------

    MODEL_PATH: str = "artifacts/model.pkl"

    ANOMALY_THRESHOLD: float = Field(
        default=0.0,
        ge=-1.0,
        le=1.0,
    )

    # ------------------------------------------------------------------
    # Internal Security
    # ------------------------------------------------------------------

    INTERNAL_API_KEY: str


@lru_cache
def get_settings() -> Settings:
    """
    Returns a singleton Settings instance.
    """
    return Settings()


settings = get_settings()