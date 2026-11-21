"""Load the selected artifact and validate its serving contract before inference."""
import hashlib
import logging
from pathlib import Path

import joblib

from app.artifacts.artifact_manager import ArtifactManager
from app.core.config import get_settings
from app.features.orchestrator import FeatureOrchestrator
from app.features.curated_orchestrator import CuratedFeatureOrchestrator
from app.services.feature_matrix_builder import FeatureMatrixBuilder
from app.services.preprocessing_service import PreprocessingService
from app.services.prediction_service import PredictionService
from app.services.shadow_recorder import ShadowAuditRecorder


def load_bundle(path, version):
    manager = ArtifactManager(path.parent)
    metadata = manager.load_metadata()
    curated = version == "v2"
    orchestrator = CuratedFeatureOrchestrator(include_severity=True) if curated else FeatureOrchestrator()
    names = list(orchestrator.feature_names)
    if (metadata.model_version != ("2.0.0-candidate" if curated else "1.0.0")
            or metadata.feature_names != names or metadata.feature_count != len(names)
            or metadata.feature_version != ("2.0.0" if curated else "1.0.0")
            or metadata.requires_scaler == curated):
        raise ValueError("Model version or ordered feature schema mismatch")
    if curated and (metadata.calibrated_decision_threshold != -0.02 or not metadata.sha256_model):
        raise ValueError("Missing or invalid V2 release metadata")
    if metadata.sha256_model and hashlib.sha256(path.read_bytes()).hexdigest() != metadata.sha256_model:
        raise ValueError("Model artifact checksum mismatch")
    model = joblib.load(path)
    if model.n_features_in_ != len(names):
        raise ValueError("Estimator feature dimension mismatch")
    scaler = manager.load_scaler() if metadata.requires_scaler else None
    if scaler is not None and scaler.n_features_in_ != len(names):
        raise ValueError("Scaler feature dimension mismatch")
    return model, FeatureMatrixBuilder(orchestrator), scaler, metadata


class PredictionBootstrap:
    @staticmethod
    def create():
        settings = get_settings()
        path = Path(settings.MODEL_PATH)
        model, builder, scaler, metadata = load_bundle(path, settings.ACTIVE_MODEL)
        expected_threshold = metadata.calibrated_decision_threshold if settings.ACTIVE_MODEL == "v2" else 0.0
        if settings.ANOMALY_THRESHOLD != expected_threshold:
            raise ValueError("Configured threshold does not match the serving release")
        candidate = Path(settings.CANDIDATE_MODEL_PATH)
        shadow = {}
        if settings.SHADOW_MODE_ENABLED and candidate.resolve() != path.resolve():
            try:
                if Path(settings.CANDIDATE_METADATA_PATH).resolve() != candidate.with_name("metadata.json").resolve():
                    raise ValueError("Candidate artifacts must share one directory")
                other, features, _, info = load_bundle(candidate, "v2")
                shadow = dict(shadow_model=other, shadow_feature_builder=features,
                              shadow_metadata=info, shadow_threshold=settings.CANDIDATE_THRESHOLD,
                              shadow_recorder=ShadowAuditRecorder(), background_shadow=True)
            except Exception:
                logging.getLogger(__name__).warning("Candidate unavailable or invalid; serving primary only")
        return PredictionService(model=model, feature_builder=builder, metadata=metadata,
                                 preprocessor=PreprocessingService(scaler),
                                 threshold=settings.ANOMALY_THRESHOLD, **shadow)
