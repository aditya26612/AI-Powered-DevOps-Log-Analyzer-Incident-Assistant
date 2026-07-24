from pathlib import Path

from app.artifacts.artifact_manager import ArtifactManager
from app.domain.model_metadata import ModelMetadata
from app.features.feature_names import FEATURE_NAMES
from app.models.model_factory import ModelFactory
from datetime import datetime, timezone

def test_artifact_manager(tmp_path: Path):
    # Arrange
    manager = ArtifactManager(tmp_path)

    model = ModelFactory.create_model()
    scaler = ModelFactory.create_scaler()

    metadata = ModelMetadata(
        model_name="IsolationForest",
        model_version="1.0.0",
        algorithm="IsolationForest",
        feature_version="1.0.0",
        feature_count=len(FEATURE_NAMES),
        feature_names=FEATURE_NAMES,
        dataset_size=10000,
        contamination=0.05,
        random_state=42,
        python_version="3.11.0",
        sklearn_version="1.9.0",
        trained_at=datetime(
            2026,
            7,
            20,
            18,
            40,
            0,
            230513,
            tzinfo=timezone.utc,
        ),
    )

    # Act
    manager.save_model(model)
    manager.save_scaler(scaler)
    manager.save_metadata(metadata)

    # Assert - Artifact existence
    assert manager.model_exists()
    assert manager.scaler_exists()
    assert manager.metadata_exists()
    assert manager.artifacts_exist()

    # Assert - Model
    loaded_model = manager.load_model()
    assert loaded_model is not None

    # Assert - Scaler
    loaded_scaler = manager.load_scaler()
    assert loaded_scaler is not None

    # Assert - Metadata
    loaded_metadata = manager.load_metadata()

    assert isinstance(loaded_metadata, ModelMetadata)

    assert loaded_metadata.model_name == metadata.model_name
    assert loaded_metadata.model_version == metadata.model_version
    assert loaded_metadata.algorithm == metadata.algorithm
    assert loaded_metadata.feature_version == metadata.feature_version
    assert loaded_metadata.feature_count == metadata.feature_count
    assert loaded_metadata.feature_names == metadata.feature_names
    assert loaded_metadata.dataset_size == metadata.dataset_size
    assert loaded_metadata.contamination == metadata.contamination
    assert loaded_metadata.random_state == metadata.random_state
    assert loaded_metadata.python_version == metadata.python_version
    assert loaded_metadata.sklearn_version == metadata.sklearn_version
    assert loaded_metadata.trained_at == metadata.trained_at