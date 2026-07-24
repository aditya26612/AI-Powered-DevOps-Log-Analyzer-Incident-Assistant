"""
===============================================================================
DevInsight AI - Model Verification
===============================================================================

Verifies that the trained model artifacts can be loaded and used
for inference.

Checks:

1. Load model.pkl
2. Load scaler.pkl
3. Load metadata.json
4. Read one sample log
5. Extract features
6. Scale features
7. Predict anomaly

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

import numpy as np

from pathlib import Path

from app.artifacts.artifact_manager import ArtifactManager
from app.data.dataset_loader import DatasetLoader
from app.features.orchestrator import FeatureOrchestrator
from app.services.preprocessing_service import PreprocessingService


def main():

    print("=" * 70)
    print("DevInsight AI - Artifact Verification")
    print("=" * 70)

    artifact_manager = ArtifactManager()

    print("Loading model...")
    model = artifact_manager.load_model()

    print("Loading scaler...")
    scaler = artifact_manager.load_scaler()

    print("Loading metadata...")
    metadata = artifact_manager.load_metadata()

    print("Metadata")
    print(metadata)

    loader = DatasetLoader()

    logs = loader.load(
        Path("data/synthetic_logs.json")
    )

    log = logs[0]

    print("\nSample Log")
    print(log)

    orchestrator = FeatureOrchestrator()



    vector = orchestrator.extract(log)

    preprocessor = PreprocessingService(scaler)

    X = np.array([vector.values], dtype=float)

    X = preprocessor.transform(X)

    prediction = model.predict(X)[0]

    print("\nPrediction")

    if prediction == -1:
        print("ANOMALY")
    else:
        print("NORMAL")

    print("=" * 70)
    print("Verification Successful")
    print("=" * 70)


if __name__ == "__main__":
    main()