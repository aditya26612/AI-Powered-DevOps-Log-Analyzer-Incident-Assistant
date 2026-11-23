# Local verification and benchmarks

The Stage 10 run verified the Flyway V9 stack on one Windows workstation with an Intel i5-12450H and 16 GiB RAM. It used a protected database restore, authenticated loopback Kafka and a local Ollama model. A loopback receiver supplied notification acknowledgements. External Slack and email were disabled.

## Automated regression

| Suite | Passed |
|---|---:|
| API Gateway | 19 |
| User Service | 9 |
| Log Layer | 129 |
| LLM Service | 195 |
| ML Pipeline | 133 |
| Spring Boot, Python and Node collectors | 5 + 7 + 7 |
| React | 7 |
| **Total** | **511** |

The full run had zero failures, errors or skips. ESLint and the Vite build also passed. The separate live workflow verified Java Logback, Python logging and Node Winston in both ingestion modes. Chrome exercised tenant switching, scoped dashboard counts, stale data and diagnosis and delivery views.

## Controlled ingestion

The runner used one HTTP writer and added one concurrent dashboard reader in the mixed profile. PostgreSQL reconciled all 336 distinct events with completed V2 results. Replay preserved log, incident, diagnosis and outbox counts. Consumer lag and unexpected dead letters were zero at the end of the revised profiles.

| Profile | Events | Requests | Persisted events/s |
|---|---:|---:|---:|
| Low | 24 | 24 | 1.98 |
| Moderate | 60 | 30 | 4.93 |
| Higher | 112 | 28 | 13.52 |
| Short burst | 40 | 8 | 17.18 |
| Mixed ingestion and reads | 80 | 40 | 8.04 |
| Database write lock and release | 20 | 4 | 10.49 |

Mixed dashboard reads had 31 ms p95 across 27 samples. The highest reported ingestion acknowledgement p95 was 63 ms among profiles with at least 20 requests. In a paired 12-sample comparison, v1 had 72.88 ms median including storage, while v2 had 62.01 ms median for broker acknowledgement. A 120-events-per-application-per-minute limit applied throughout. After a first run encountered HTTP 429, the revised run isolated application quotas by profile.

## Reliability and reproduction

- V2 returned HTTP 503 during the broker outage. V1 still persisted an event. A retry with the same v2 event ID later persisted.
- Twenty acknowledged events accumulated behind a database write lock and persisted 0.546 seconds after release.
- A replacement diagnosis worker completed the same job following an expired lease.
- An interrupted notification stayed in an uncertain state without automatic resend. A confirmed retry demonstrated the possibility of duplicate provider requests.

Follow the [Windows setup guide](SETUP_GUIDE.md) and the [Kafka startup reference](../README.md#optional-local-kafka-intake). The benchmark and fault options are exposed by [the guarded fixture launcher](../scripts/test-stage08.ps1). They require a disposable V9 restore on port 15438, a matching data-directory identity, the independently named Kafka fixture and disabled external delivery.

## Production limits

The measurements describe the tested local profiles. Distributed load, broker high availability, pool exhaustion, external delivery, longer browser runs and formal accessibility remain unverified. Browser bearer-token storage and restrictive frontend security headers remain open issues. A successful diagnosis is advisory and does not execute remediation.
