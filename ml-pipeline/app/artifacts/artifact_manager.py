"""
===============================================================================
Artifact Manager
===============================================================================
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

import joblib
from datetime import datetime

from app.artifacts.artifact_validator import ArtifactValidator
from app.artifacts.paths import (
    ARTIFACT_DIRECTORY,
)

from app.domain.model_metadata import ModelMetadata
from dataclasses import asdict, fields

class ArtifactManager:

    def __init__(
        self,
        artifact_directory: Path = ARTIFACT_DIRECTORY,
    ) -> None:

        art_dir = Path(artifact_directory)
        if not art_dir.exists() and (Path("ml-pipeline") / art_dir).exists():
            art_dir = Path("ml-pipeline") / art_dir

        self._artifact_directory = art_dir

        self._model_file = (
            self._artifact_directory / "model.pkl"
        )

        self._scaler_file = (
            self._artifact_directory / "scaler.pkl"
        )

        self._metadata_file = (
            self._artifact_directory / "metadata.json"
        )

        self._artifact_directory.mkdir(
            parents=True,
            exist_ok=True,
        )

    # ==========================================================
    # Model
    # ==========================================================

    def save_model(
        self,
        model,
    ) -> str:

        joblib.dump(
            model,
            self._model_file,
        )
        with self._model_file.open("rb") as artifact:
            return hashlib.file_digest(artifact, "sha256").hexdigest()

    def load_model(self):

        ArtifactValidator.validate(
            self._model_file
        )

        return joblib.load(
            self._model_file
        )

    # ==========================================================
    # Scaler
    # ==========================================================

    def save_scaler(
        self,
        scaler,
    ) -> None:

        joblib.dump(
            scaler,
            self._scaler_file,
        )

    def load_scaler(self, allow_missing: bool = False):

        if not self._scaler_file.exists():
            if allow_missing:
                return None
            try:
                metadata = self.load_metadata()
                if not getattr(metadata, "requires_scaler", True):
                    return None
            except Exception:
                pass

        ArtifactValidator.validate(
            self._scaler_file
        )

        return joblib.load(
            self._scaler_file
        )

    # ==========================================================
    # Metadata
    # ==========================================================

    

    def save_metadata(
        self,
        metadata: ModelMetadata,
    ) -> None:

        payload = asdict(metadata)

        payload["trained_at"] = metadata.trained_at.isoformat()

        with open(
            self._metadata_file,
            "w",
            encoding="utf-8",
        ) as fp:

            json.dump(
                payload,
                fp,
                indent=4,
            )

    def load_metadata(self):

        ArtifactValidator.validate(
            self._metadata_file
        )

        with open(
            self._metadata_file,
            encoding="utf-8",
        ) as fp:

            payload = json.load(fp)

            if isinstance(payload.get("trained_at"), str):
                payload["trained_at"] = datetime.fromisoformat(
                    payload["trained_at"]
                )

            valid_fields = {f.name for f in fields(ModelMetadata)}
            filtered = {k: v for k, v in payload.items() if k in valid_fields}
            return ModelMetadata(**filtered)

    # ==========================================================
    # Validation
    # ==========================================================
    def model_exists(self) -> bool:
        return self._model_file.exists()


    def scaler_exists(self) -> bool:
        return self._scaler_file.exists()


    def metadata_exists(self) -> bool:
        return self._metadata_file.exists()



    def artifacts_exist(self) -> bool:

        if not (self._model_file.exists() and self._metadata_file.exists()):
            return False

        if self._scaler_file.exists():
            return True

        try:
            metadata = self.load_metadata()
            return not getattr(metadata, "requires_scaler", True)
        except Exception:
            return False
