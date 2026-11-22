# Dashboard metrics

| Signal | Counting rule |
|---|---|
| Persisted logs | One row per visible source event with occurrence in the range |
| V2 anomalies | Matching log rows with completed scoring, candidate model version and anomaly=true |
| Operational errors | Matching persisted ERROR or FATAL rows |
| Scoring failures | Matching rows marked FAILED |
| Open incidents | Current OPEN incidents whose last occurrence falls inside the selected range |
| Diagnosis | Current authoritative states of durable jobs created in the range |
| Slack | Current states of outbox deliveries created in the range. Attention includes unknown and terminal failures |

Log and incident occurrence predicates use the stored UTC contract of the external collector path. Legacy logs use stored wall-clock timestamps without offset. The [time contract](STAGE_09_OBSERVABILITY_API_CONTRACTS.md) records this limitation. Replay idempotency exists in the log table, so aggregation counts persisted rows directly. A cross-application incident is selected by actual member application. Diagnosis and Slack use the owning application.

Supported ranges are 1 hour, 24 hours, 7 days, and 30 days. UTC epoch-aligned buckets use 5 minutes, 1 hour, 6 hours and 1 day respectively. Bounds are inclusive from and exclusive to. The first and last bins can be partial. Chart labels use the browser time zone. Recent activity combines stored incident creation, acknowledgement, resolution, successful diagnosis, and receiver acknowledgement timestamps. Broker request totals, host resource use, and Slack read receipts are unavailable.
