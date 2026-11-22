# DevInsight continuation checkpoint

Stage 09 is complete for local development on Flyway V9 with 497 passing backend and collector tests, seven passing frontend tests, and verified Chrome workflows. Use the [Stage 09 continuation](docs/stage-09/STAGE_09_CONTINUATION.md), [test evidence](docs/stage-09/STAGE_09_TEST_EVIDENCE.md), and [Stage 10 handoff](docs/stage-09/STAGE_10_HANDOFF.md). Stage 10 requires explicit approval.

Stage 08 established the historical V9 baseline with 494 passing tests. Its [continuation](docs/stage-08/STAGE_08_CONTINUATION.md) and [test evidence](docs/stage-08/STAGE_08_TEST_EVIDENCE.md) are preserved.

Stage 07 established the historical V7 baseline with 461 passing tests. Its [continuation](docs/stage-07/STAGE_07_CONTINUATION.md) and [test evidence](docs/stage-07/STAGE_07_TEST_EVIDENCE.md) are preserved.

Stage 06 established the historical V6 baseline with 447 passing tests. See the [Stage 06 continuation](docs/stage-06/STAGE_06_CONTINUATION.md) and [test evidence](docs/stage-06/STAGE_06_TEST_EVIDENCE.md).

Stage 05 is complete for local development with 440 passing tests. Start with the [Stage 05 continuation](docs/stage-05/STAGE_05_CONTINUATION.md), [verification](docs/stage-05/STAGE_05_TEST_EVIDENCE.md), and [Stage 06 handoff](docs/stage-05/STAGE_06_HANDOFF.md). Stage 06 requires explicit approval.

Stage 04 is complete for local development with 402 passing tests. Use the [Stage 04 continuation](docs/stage-04/STAGE_04_CONTINUATION.md), [Windows setup](docs/stage-04/STAGE_04_LOCAL_SETUP.md), and [Stage 05 handoff](docs/stage-04/STAGE_05_HANDOFF.md). The original Stage 03 verification is preserved below.

## Stage 03 checkpoint

Stage 03 is complete for local development, with 394 passing tests and the verified local V5 cutover. Stage 02 remains closed. See [the current continuation](docs/stage-03/STAGE_03_CONTINUATION.md), [verification evidence](docs/stage-03/STAGE_03_TEST_EVIDENCE.md), and [local setup](docs/stage-03/STAGE_03_LOCAL_SETUP.md). The previous Stage 02 results remain under docs/stage-02.

## Earlier V2 checkpoint

The workspace reached checkpoint 3 with V2 local activation, documentation, career materials, and an archive verification workflow. Git operations were read-only. The [engineering report](docs/FINAL_ENGINEERING_REPORT.md) describes the working-tree changes and [changed-file manifest](docs/CHANGED_FILE_MANIFEST.md) lists files.

## Completed verification

- Python non-HTTP suite, real packaged V2 and V1 scoring and rollback.
- Targeted Log Layer regressions for mapping, FATAL parsing, inference failure and diagnosis persistence after commit.
- Targeted LLM Service regressions for retrieval, prompts, parsing and orchestration.
- Historical benchmark sample and arithmetic review, including failed diagnosis evidence.
- Publication scan and archive checks as recorded in the [release verification evidence](docs/verification/release-review.json).

The [test manifest](docs/verification/final-test-manifest.json) has executed commands, report paths, timing and counts. The previous HTTP attempt had loopback permission errors and an event-loop stall. API Gateway and User Service were outside the final targeted run.

## Remaining live checks

1. Execute [the four Windows smoke scenarios](docs/SETUP_GUIDE.md#controlled-live-validation) against the disposable database and loopback services. Verify the new PostgreSQL diagnosis columns and check that an unavailable model records `analysisStatus=FAILED`.
2. From `ml-pipeline` run `python -m pytest -q` where Windows loopback is available. Run the four Maven module suites using [the documented commands](docs/TESTING.md#reproduce-the-local-tests). Record distinct fresh results.
3. Attach only sanitized results. Keep the measured workload, model version and SHA-256 with any new latency or diagnosis claim.

Independent operational labels and production deployment are outside this local release. A fresh Git clone needs the model bundles supplied by the release archive or another trusted artifact source.
