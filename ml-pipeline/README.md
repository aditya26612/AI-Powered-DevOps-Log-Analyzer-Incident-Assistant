# DevInsight ML Pipeline

FastAPI scores structured application logs with a scikit-learn Isolation Forest. Spring Boot calls the single-log or batch prediction endpoint and stores the returned decision score, label, and model version with the log.

## Start V2 locally

From `ml-pipeline`, use Python 3.11. Confirm the artifact files listed below are present in your checkout before starting the service.

```powershell
python -m pip install -r requirements.txt
$env:ACTIVE_MODEL = 'v2'
$env:INTERNAL_API_KEY = '<same local key as the Log Layer>'
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

```powershell
Invoke-RestMethod http://127.0.0.1:8000/api/v1/system/health
Invoke-RestMethod http://127.0.0.1:8000/api/v1/system/model
```

| Model | Artifact directory | Features | Scaler | Default cutoff |
|---|---|---:|---|---:|
| `v2` / `2.0.0-candidate` | `artifacts/candidates/v2_curated_isolation_forest/` | 40 | Optional artifact, unused in inference | `-0.02` |
| `v1` / `1.0.0` | `artifacts/` | 41 | Required `scaler.pkl` | `0.0` |

Both bundles need `model.pkl` and `metadata.json`. Use trusted artifacts, since Joblib deserializes Python objects. The bundled V2 was trained with scikit-learn 1.8.0. The V1 metadata records 1.9.0, and loading it under 1.8.0 emitted version warnings during the rollback check.

`POST /api/v1/predict` accepts `timestamp`, `level`, `service_name`, and `message`. `POST /api/v1/predict/batch` accepts these objects in a `logs` array. Set the same non-empty `INTERNAL_API_KEY` in the Log Layer and ML service and send it as `X-API-Key` on both routes. Predictions return HTTP 503 if the server key is unset and HTTP 401 if the request key is missing or incorrect. A score strictly below the active cutoff yields `prediction=-1` and `is_anomaly=true`. `/api/v1/system/model` reports both the metadata cutoff and the cutoff active in the process.

## Roll back to V1

Stop the local ML process, then restart with `ACTIVE_MODEL=v1`. Clear `MODEL_PATH` and `ANOMALY_THRESHOLD` overrides from the environment and `.env` when you want the V1 defaults. The model endpoint should report `1.0.0`, 41 features, `requires_scaler=true`, and `active_threshold=0.0`. Set `ACTIVE_MODEL=v2` and restart again to restore V2.

With V1 primary, `SHADOW_MODE_ENABLED=true` compares it against the V2 candidate in a bounded background task. A comparison can be dropped while another batch is running. The primary response remains authoritative. Shadow output records model comparison fields and excludes raw log bodies and identifiers.

## Verify and train offline

```powershell
python -m pip install pytest httpx
python -m pytest -q
python -m app.training.train --feature-set curated --split-strategy template_grouped --decision-threshold=-0.02
```

Training defaults to `artifacts/training-runs/`, separate from both packaged serving bundles. Use `--output-dir` for an individual experiment or `--no-save` to evaluate in memory. A new training run can produce a different artifact from the checked V2 bundle. To try a compatible experimental model, use `MODEL_PATH` for its `model.pkl`, place `metadata.json` beside it, and set `ANOMALY_THRESHOLD` to the experiment's evaluated cutoff.

See the [ML Pipeline architecture](../system-architecture/ml-pipeline/INTERNAL_ARCHITECTURE.md) and [dataset provenance](ml_finalization_results/DATASET_PROVENANCE_AND_QUALITY.md).
