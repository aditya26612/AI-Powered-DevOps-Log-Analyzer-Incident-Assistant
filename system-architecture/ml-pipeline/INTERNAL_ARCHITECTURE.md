# ML Pipeline internals

FastAPI serves the anomaly model on port 8000. Prediction routes require the internal service key and are intended for Log Layer. The model and health metadata routes are available inside the deployment network.

| Route | Role |
|---|---|
| `POST /api/v1/predict` | Score one event |
| `POST /api/v1/predict/batch` | Score a bounded batch |
| `GET /api/v1/system/health` | Report service health |
| `GET /api/v1/system/model` | Identify the active artifact and feature contract |

## Model and feature contract

`ACTIVE_MODEL=v2` selects `2.0.0-candidate`, a 40-feature Isolation Forest with threshold `-0.02`. Model bootstrap validates artifact metadata, feature order and estimator shape. V2 also checks the artifact digest. V1 remains available for explicit rollback. The [ML guide](../../ml-pipeline/README.md) covers serving, validation and training separately.

## Processing boundary

The prediction service applies preprocessing and feature extraction before scoring. The response includes the model version used by Log Layer. Log Layer separately evaluates operational errors and controls incident eligibility. Inference failure does not produce a completed score or fictitious model version. The container packages the existing artifacts without retraining them.
