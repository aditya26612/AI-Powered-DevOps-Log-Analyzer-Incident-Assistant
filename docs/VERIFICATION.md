# Local verification and benchmarks

## Clean-copy Compose smoke and HTTP replay

A temporary copy of the current candidate files, including the Log Layer bootstrap SQL, built all six application images. The run used Docker Desktop, a fresh PostgreSQL volume in a dedicated Compose project, and unused loopback ports. It used the existing host Ollama server through the [temporary override](../system-architecture/CONTAINER_DEPLOYMENT.md#use-ollama-already-running-on-the-docker-desktop-host). The bundled Ollama image download and full bundled startup remain unverified.

User Service Flyway V1 and Log Layer V1–V9 applied successfully. PostgreSQL confirmed that the Log Layer runtime login inherits the tenant role, owns no application tables, and lacks superuser and RLS-bypass privileges. Seven tables enforce forced RLS. The role returned zero source logs without tenant scope after ingestion. The pgvector runbook schema initialized and the fixture indexed 253 chunks. All six application health checks passed.

| Synthetic tenant | Source logs | Incidents | Diagnosis jobs |
|---|---:|---:|---:|
| Before ingestion | 0 | 0 | 0 |
| After the original Node collector batch | 3 | 1 | 1 |
| After replaying the same HTTP v1 request | 3 | 1 | 1 |

Both requests returned HTTP 200. The replay returned three duplicate acknowledgements with the original log IDs. The local Ollama diagnosis succeeded on its first attempt and persisted a nonempty summary. Slack and email delivery were disabled. A short recording, screenshot, and sanitized replay receipt were saved locally.

The Node example's stock clean install reproduced an npm lockfile sync error. The example now specifies npm's packaged local dependency mode, which matches the existing lockfile and lets the Winston peer dependency resolve. A second clean install succeeded, and all seven Node collector tests passed.

## Local V11 Loki verification

Two runs cover the owner-managed endpoint and replay with real Grafana Loki 3.5.0. These results are separate from the earlier V9 HTTP and Kafka rehearsals.

- **Isolated V10-to-V11 upgrade.** Flyway applied V11 to an existing fixture with three synthetic logs. In Chromium the organization owner saved a second approved origin, reloaded the page, and confirmed the saved URL. The application kept its selector and original rows, imported one new log, and replay skipped one duplicate with the same four persisted IDs. Unauthenticated requests, an organization admin attempting endpoint edits, an unbound organization, and an unapproved origin were rejected. The runtime database role saw zero connection rows without tenant scope.
- **Existing local Compose stack, November 27, 2026.** The operator verified a backup archive before applying V11. Flyway confirmed the upgrade from V10, Log Layer and the frontend rebuilt and became healthy, and Gateway reported `UP`. A read-only SQL query confirmed the organization endpoint was stored in PostgreSQL. The owner used the `pull-demo` selector and confirmed automatic import and replay in the UI.

| Existing local stack | Loki logs imported | Duplicates skipped | Rows matching `loki-v11-670e834b` | Log ID |
|---|---:|---:|---:|---|
| Before replay | 3 | 1 | 1 | `9` |
| After replay | 3 | 2 | 1 | `9` |

The Loki push returned HTTP 204 in the local smoke. That status confirms acceptance by Loki. The Log Explorer row confirms DevInsight persistence, and the counter change and stable ID confirm deduplication on replay. External Slack and email delivery were disabled.

The focused V11 regression recorded **99 passing Log Layer tests** (31 fixture-gated skips), **7 passing frontend tests**, a successful frontend build, targeted ESLint, and quiet Compose validation. These counts are from the V11 work and are separate from the full historical 511-test run. Hosted Grafana authentication, late-arrival backfill guarantees, and production or multi-node operation were outside the V11 runs. [Run the Loki smoke test](LOKI_LOG_SOURCES.md)

## Prior Stage 10 fixture

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

Follow the [Windows setup guide](SETUP_GUIDE.md) and the [loopback Kafka launcher](../scripts/setup-kafka-local.ps1). The benchmark and fault options are exposed by [the guarded fixture launcher](../scripts/test-stage08.ps1). They require a disposable V9 restore on port 15438, a matching data-directory identity, the independently named Kafka fixture and disabled external delivery.

## Production limits

The measurements describe the tested local profiles. Distributed load, broker high availability, pool exhaustion, external delivery, longer browser runs and formal accessibility remain unverified. Browser bearer-token storage and restrictive frontend security headers remain open issues. A successful diagnosis is advisory and does not execute remediation.
