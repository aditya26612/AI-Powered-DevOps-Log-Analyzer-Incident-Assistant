# Notification test evidence

The Stage 07 baseline recorded 461 passing tests. The Stage 08 targeted run passed 15 PostgreSQL and HTTP integration cases and 18 transport safety cases. The final module counts are taken from fresh report XML rather than added to the historical total.

| Module | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| API Gateway | 19 | 0 | 0 | 0 |
| User Service | 9 | 0 | 0 | 0 |
| Log Layer | 119 | 0 | 0 | 0 |
| LLM Service | 195 | 0 | 0 | 0 |
| Python ML | 133 | 0 | 0 | 0 |
| Spring Boot collector | 5 | 0 | 0 | 0 |
| Python collector | 7 | 0 | 0 | 0 |
| Node.js collector | 7 | 0 | 0 | 0 |
| **Total** | **494** | **0** | **0** | **0** |

Maven ran `mvn -f <module>/pom.xml -Dsurefire.reportNameSuffix=stage08 test` for Gateway, User Service, Log Layer, LLM Service and the Spring Boot collector. Log Layer used the restricted runtime role, the V9 fixture and the authenticated Kafka broker. Reports are `target/surefire-reports/TEST-*-stage08.xml` beneath each module. The collector reports are from a separate protected copy of its module.

Pytest ran the ML and Python collector suites with `--junitxml`. Node.js ran `node --test --test-reporter=junit`. The protected run directory retains `ml-stage08.xml`, `collector-python.xml`, `collector-node.xml`, and the XML aggregate `test-summary-stage08.json`. The live fault receipt `stage08-v9-live.json`, migration logs, manual compatibility checks, browser workflow, and frontend build are separate evidence.

## Controlled scenarios

| Scenarios | Evidence |
|---|---|
| A–C and W | The actual Python collector sent 12 related errors through Gateway and authenticated Kafka. V2-scored logs produced one incident, one Ollama result and one notification. Replaying the original event IDs preserved 12 members and one job. The persisted intent survived the diagnosis JVM exiting. |
| D and I | The real PostgreSQL suite rolled back the accepted result and outbox together, then accepted one result after retry. It recovered a pre-dispatch lease and rejected success from an expired worker. |
| E–H and V | The real worker processes and loopback receiver covered pre-dispatch termination, termination during a request, lost response, termination with an accepted response and blocked final commit, and disposable PostgreSQL interruption. The provider-ambiguous cases persisted `DELIVERY_UNKNOWN`. |
| J and K | Two worker JVMs contested recovery. Database tests exercised concurrent claims and the cross-application cooldown for identical webhook URLs without creating an extra attempt while held. Other destinations progressed during the cooldown. |
| L–N | The receiver exercised retry followed by success, bounded retry exhaustion, actual 400/403/404/410, 503 and 429, malformed success, and an oversized response. |
| O–Q | A real signed owner request needed confirmation for unknown delivery. Concurrent confirmations returned 202 and 409. Java integration tests checked viewer denial and cancellation on destination rotation and deactivation. |
| R and S | A multi-application incident sent one generic notice through the diagnosis owner. Identical trace evidence across two organizations produced independent incidents and delivery records. |
| T and U | Gateway configuration rejected unsafe loopback and ambiguous hostnames. The receiver confirmed redirects were never followed and injected mentions, URL and credential patterns were absent from messages. |
| X and Y | Migrated fresh and populated copies started with zero notification rows. The automatic runtime uses the outbox worker. The receiver observed a single request in the primary run. |
| Z | The live contract returned 201 for single intake, 200 for synchronous batch and 202 for Kafka. Live model status reported `2.0.0-candidate`, 40 features and threshold `-0.02`. The Java suites cover the existing diagnosis, incident lifecycle and organization behavior. |

Chrome showed persisted delivery and attempts, displayed uncertainty, and requested confirmation before retry. The Vite production build and lint on all changed frontend files passed. Full frontend lint reported five existing errors in `AuthContext.jsx`, `Dashboard.jsx`, and `LogInspector.jsx`. Those files matched the pre-stage Git revision.

The ML suite emitted version compatibility warnings for packaged scikit-learn estimators and sample-size warnings from synthetic training tests. The live model configuration and artifacts were preserved. No real Slack or email request was used for verification.
