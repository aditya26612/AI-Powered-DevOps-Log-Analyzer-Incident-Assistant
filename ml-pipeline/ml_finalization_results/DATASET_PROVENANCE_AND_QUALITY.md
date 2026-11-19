# DATASET PROVENANCE AND QUALITY REPORT

## 1. Executive Summary & Provenance Rectification

This document establishes the verified origins, ground-truth labeling methodologies, and data quality boundaries across all datasets in the DevInsight machine learning subsystem.

### Rectification of Misleading Provenance
Prior audit iterations uncovered a severe methodological integrity breach: `tools/generate_datasets.py` programmatically assigned the fields:
```json
"label_source": "human_sre_annotator",
"reviewer_id": "sre-lead-01"
```
to programmatically synthesized log entries. This falsely implied independent human verification by a senior site reliability engineer.

**Immediate Remediation Executed:**
1. All generated records in `data/labeled_operational_logs.jsonl` and `tools/generate_datasets.py` have been updated with truthful metadata:
   - `label_source`: `"synthetic_scenario"`
   - `review_status`: `"not_human_verified"` (for adjudicated scenarios) and `"needs_adjudication"` (for ambiguous probes)
   - `reviewer_id`: `null` (no fabricated human identity)
2. `DatasetLoader.load()` was corrected to preserve `is_anomaly = None` for records with `ground_truth == "UNCERTAIN"` or `is_anomaly = null`, eliminating the previous defect that silently converted uncertain records into normal traffic (`is_anomaly = False`).
3. An audit-compliant human annotation workflow (`tools/annotation_workflow.py`) has been upgraded to enforce mandatory operational rationales, reviewer attribution, and automated exclusion of unadjudicated ambiguous logs from binary classification metrics.

---

## 2. Dataset Inventory & Integrity Checksums

| Dataset Identifier | File Path | Format | Record Count | Anomalies | Normal | Uncertain | SHA-256 Checksum |
|---|---|---|---|---|---|---|---|
| **Dataset A (Historical Synthetic)** | `data/synthetic_logs.json` | JSON | 9,999 | 509 (5.09%) | 9,490 (94.91%) | 0 | `2dc711f213fbeafae0fa82b2d557b88977380488e3ac1db4e5b8260a057cc61c` |
| **Dataset B (Operational Scenarios)** | `data/labeled_operational_logs.jsonl` | JSONL | 215 | 64 (29.77%) | 149 (69.30%) | 2 (0.93%) | `01d08117afe00dc7ca16515505e55a931d70352b5541c9cebf1ed6df3ad0d710` |
| **Dataset B (Adjudicated Subset)** | Memory / Extracted | In-Memory | 213 | 64 (30.05%) | 149 (69.95%) | 0 (0.00%) | N/A (Derived via `is_anomaly is not None`) |
| **Dataset C (Challenge Diagnostic)** | `data/operational_challenge_scenarios.jsonl` | JSONL | 39 | 24 (61.54%) | 15 (38.46%) | 0 | `b0bf0a5ae8e811d67525d4f7774aaf52406e499082f70f51f39491e8693dfc8b` |

---

## 3. Dataset Category Taxonomy & Roles

To prevent methodological confusion, the subsystem enforces five distinct data categories:

### Category A: Historical Synthetic Training Data (`data/synthetic_logs.json`)
- **Origin**: Programmatically generated log events simulating microservice traffic.
- **Structural Confounder**: In this dataset, log severity was 100% correlated with anomaly status:
  - `DEBUG` (1,637 logs): 0 anomalies (0.0%)
  - `INFO` (7,853 logs): 0 anomalies (0.0%)
  - `WARN` (176 logs): 176 anomalies (100.0%)
  - `ERROR` (320 logs): 320 anomalies (100.0%)
  - `FATAL` (13 logs): 13 anomalies (100.0%)
- **Consequence**: Any unsupervised or supervised model trained exclusively on this data learned that severity alone is sufficient for anomaly prediction. It ignored message content and cannot detect silent critical failures logged at `INFO` or benign handled errors logged at `WARN`/`ERROR`.

### Category B: Decoupled Operational Scenarios (`data/labeled_operational_logs.jsonl`)
- **Origin**: Programmatic behavioral test fixture synthesizing realistic production incidents and benign edge cases.
- **Severity Decoupling**: Explicitly breaks the severity confounder:
  - Benign handled errors/warnings (`WARN`/`ERROR` -> `NORMAL`): 45 records (cache misses, handled 404s, client rate limits, parameter validation typos).
  - Silent critical incidents (`INFO` -> `ANOMALOUS`): 29 records (worker thread pool deadlocks, transaction stalls, brute force login bursts, backpressure queue pileups).
  - Operational incidents (`ERROR`/`FATAL` -> `ANOMALOUS`): 35 records (connection pool exhaustion, OOMKills, pod CrashLoopBackOff, disk full).
  - Healthy operations (`INFO`/`DEBUG` -> `NORMAL`): 104 records.
  - Ambiguous probes (`UNCERTAIN`): 2 records.
- **Evaluation Status**: Useful for behavioral evaluation and diagnostic debugging, but **not proof of production generalization** because the examples are simulated scenarios.

### Category C: Challenge Diagnostic Probes (`data/operational_challenge_scenarios.jsonl`)
- **Origin**: 39 curated edge-case probes across 6 operational categories:
  1. `database_failures` (7 probes)
  2. `distributed_systems` (7 probes)
  3. `infrastructure` (7 probes)
  4. `security_and_auth` (5 probes)
  5. `application_behavior` (7 probes)
  6. `adversarial_and_robustness` (6 probes)
- **Role**: Unit-level behavioral regression probes testing specific failure signatures, unicode inputs, whitespace-only messages, and off-hour shifts.

### Category D: Shadow-Mode Operational Review Queue (`artifacts/shadow_audit.jsonl`)
- **Origin**: Live or replayed application logs evaluated simultaneously by V1 (deployed) and V2 (candidate in shadow mode).
- **Privacy Enforcement**: Stores strictly anonymized metadata (`event_id`, `timestamp`, `service_name`, model versions, scores, predictions, disagreement flag). Never persists raw message text, tokens, or PII.

### Category E: Locked Final Evaluation Partition
- **Status**: Formally segregated split withheld from model selection, hyperparameter sweeps, and threshold tuning.

---

## 4. Handling of Ambiguous & Uncertain Ground Truth

The 2 ambiguous records identified in Dataset B are:
1. `log_id`: `1c9cae1a-a3d3-4b86-b648-59e7e120c121`
   - `level`: `ERROR`, `service`: `api-gateway`
   - `message`: `HTTP 500 Internal Server Error returned to client IP 10.12.0.8`
   - `rationale`: An isolated 500 error may be an unhandled client request bug or major outage; a single log line is insufficient to establish operational impact without trace context.
2. `log_id`: `318a9c13-33c8-4127-8ccb-23f1dbdff455`
   - `level`: `WARN`, `service`: `user-service`
   - `message`: `Slow query detected: SELECT * FROM audit_logs took 1250ms`
   - `rationale`: A 1.25s query on an audit log table could be a scheduled background batch or query index degradation.

**Evaluation Protocol:**
- In standard binary classification metrics (Precision, Recall, F1, ROC-AUC, PR-AUC), these records are **strictly excluded**.
- In historical reproduction checks (Full 215 records), they are documented explicitly to explain why V1 had FP=1 and V2 had FP=25 in earlier audits.

---

## 5. Prevalence and Operational Limitations

A critical data science principle must be maintained: **Dataset B is an artificially balanced development benchmark (30.0% anomaly prevalence), not a representative production distribution.**

- In real enterprise microservices, anomaly prevalence is typically **0.01% to 0.1%** (1 in 1,000 to 1 in 10,000 logs).
- On Dataset B, V2 produces 24 false positives out of 149 normal logs, giving a **False Positive Rate (FPR) of 16.11%** (161 false alerts per 1,000 normal logs).
- **Operational Reality**: If deployed naively into a system processing 100,000 logs per hour, a 16.1% FPR would generate **16,100 false alarms per hour**, overwhelming SRE on-call engineers.
- Therefore, threshold calibration and shadow-mode staging are strictly required before any candidate model can be considered for production promotion.
