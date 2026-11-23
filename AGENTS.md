# DevInsight — Codex Project Instructions

## Project
DevInsight is an AI-powered DevOps monitoring platform using Java 21, Spring Boot, Spring Cloud Gateway, Python FastAPI, PostgreSQL/pgvector, Ollama, and React.

## Completed work
- Stage 01: Architecture audit, security findings, database inventory, V2 ML baseline, testing, and modernization roadmap.
- Stage 01.5: Security hardening, local secret automation, PowerShell scripts, additional tests, and documentation.
- Stage 02: Tenant isolation, application onboarding, scoped credentials and the local PostgreSQL cutover. The historical verification total is 378.
- Stage 03: External log collectors, bounded batch ingestion and event idempotency. Verified local V5 cutover and 394 passing tests.
- Stage 04: Local Kafka KRaft, v2 broker acknowledgements, consumer replay and DLQ. Verified with 402 passing tests.
- Stage 05: Shared parsing and inference, pinned V2 feature validation, operational triage and real HTTP/Kafka verification. Verified with 440 passing tests.
- Stage 06: Tenant-safe incident correlation, event-time windows, lifecycle and diagnosis coordination. Verified local V6 cutover and 447 passing tests.
- Stage 07: Rolling incident correlation, reported W3C trace context and durable incident-aware Ollama diagnosis. Verified local V7 cutover and 461 passing tests.
- Stage 08: Tenant-scoped transactional Slack outbox, shared destination cooldowns, durable attempts, and explicit handling of uncertain delivery. Verified local V8 and V9 cutover and 494 passing tests.
- Stage 09: Tenant-scoped overview, log explorer, and incident workspace with diagnosis and delivery visibility. Verified on V9 with 497 passing backend and collector tests, seven frontend tests, and Chrome.
- Stage 10: Local end-to-end, recovery, security and benchmark verification, with focused source-preview masking. The fresh V9 regression passed 504 backend and collector tests and seven frontend tests.
- Verify reported completion against source and actual test results.

## Current task
Use docs/stage-10 for local verification evidence, unverified cases and the Stage 11 handoff. Stage 10 changes remain uncommitted. Stage 11 requires explicit authorization.

## Remaining roadmap
Stage 11: Documentation and interview preparation.

## Important rules
- NEVER run git add, commit, push, merge, rebase, reset or clean.
- Never deploy automatically or create pull requests.
- Never delete or reset PostgreSQL data.
- Preserve V1/V2 models, datasets, and RAG knowledge.
- Do not retrain V2 or change the threshold to pass tests.
- Never expose `.env.local`, JWT secrets, API keys, passwords, or Slack tokens.
- Do not send real Slack notifications.
- Make minimal changes.
- Do not start another stage without explicit approval.

## Technical baseline
- API Gateway: 8080
- User Service: 8081
- Log Layer: 8082
- LLM Service: 8084
- ML Pipeline: 8000
- Frontend: 5173
- V2 model: 2.0.0-candidate, 40 features, threshold -0.02.
- Model environment variable: ACTIVE_MODEL=v2.

## Reference documentation
Read `docs/stage-01-baseline/` including:
- CURRENT_ARCHITECTURE.md
- TARGET_ARCHITECTURE.md
- STAGE_01_5_SECURITY_CORRECTIONS.md
- STAGE_01_5_TEST_EVIDENCE.md
- SECURITY_FINDINGS.md
- DATABASE_SCHEMA_AND_MIGRATION_PLAN.md
- STAGE_02_HANDOFF.md

Read docs/stage-03 for the v1 batch contract and docs/stage-04 for the optional v2 Kafka mode and evidence.
Read docs/stage-05 for the current scoring, parser, and operational triage contracts.
Read docs/stage-06 for the incident API, correlation policy, migration evidence and Stage 07 handoff.
Read docs/stage-07 for rolling correlation, tracing, durable diagnosis, local V7 evidence and the Stage 08 handoff.
Read docs/stage-08 for the notification outbox, V8 and V9 migration evidence, real loopback delivery and recovery results, and the Stage 09 handoff.
Read docs/stage-09 for the observability workspace, tenant read contracts, browser evidence, and the Stage 10 handoff.
Read docs/stage-10 for the regression results, local performance envelope, fault scenarios, security review and Stage 11 handoff.

Documentation is not automatically authoritative; verify against source code and test evidence.

## Completion reporting
Always provide changed files, test results, unresolved issues and next steps.
