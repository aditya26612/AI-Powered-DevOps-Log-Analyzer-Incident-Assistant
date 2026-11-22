# Stage 09 test evidence

Stage 08 recorded 494 backend and collector tests. Stage 09 added three PostgreSQL-backed HTTP and aggregation cases covering exact time edges, replay, overlapping tenants, stable pages, input bounds, failed scoring, and credential masking. The existing diagnosis suite also checks persisted accepted-result details.

| Suite | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| API Gateway | 19 | 0 | 0 | 0 |
| User Service | 9 | 0 | 0 | 0 |
| Log Layer | 122 | 0 | 0 | 0 |
| LLM Service | 195 | 0 | 0 | 0 |
| Python ML pipeline | 133 | 0 | 0 | 0 |
| Spring Boot collector | 5 | 0 | 0 | 0 |
| Python collector | 7 | 0 | 0 | 0 |
| Node.js collector | 7 | 0 | 0 | 0 |
| **Backend and collectors** | **497** | **0** | **0** | **0** |
| Frontend component and client | 7 | 0 | 0 | 0 |

Maven ran `mvn -f <module>/pom.xml -Dsurefire.reportNameSuffix=stage09 test`. The final Log Layer run uses suffix `stage09final` and the restricted V9 PostgreSQL role, authenticated Kafka and controlled receiver. The Spring collector ran from a protected source copy. Relevant XML is in the respective `target/surefire-reports` directories.

Python ML and collector suites ran with `python -m pytest -q --junitxml=<output>`, and the Node collector and frontend used Node's JUnit reporter. The protected copies retain `stage09-ml.xml`, `stage09-collector-python.xml`, `stage09-collector-node.xml`, `frontend-tests-final.xml` and `stage09-test-summary.json`. The full frontend lint and Vite production build passed separately. The [browser report](STAGE_09_BROWSER_TEST_EVIDENCE.md) records separate live evidence.

The ML tests emitted existing scikit-learn artifact version and synthetic sample-size warnings. The real prediction contract and candidate artifacts were preserved.
