# Stage 09 continuation

Use the [implementation decisions](STAGE_09_IMPLEMENTATION_DECISIONS.md), [observability API contract](STAGE_09_OBSERVABILITY_API_CONTRACTS.md), and [local workflow](STAGE_09_LOCAL_SETUP.md). Stage 08's Flyway V9 and durable delivery history are preserved. Stage 10 requires explicit authorization.

## File inventory

| Area | Stage 09 files |
|---|---|
| Backend source | `log-layer/src/main/java/com/project/log_layer/observability/ObservabilityController.java`, `ObservabilityQueries.java`, `incident/IncidentController.java`, `incident/IncidentQueries.java`, `diagnosis/DiagnosisApi.java` |
| Backend tests | `log-layer/src/test/java/com/project/log_layer/incident/IncidentIntegrationTest.java`, `diagnosis/DiagnosisQueueIntegrationTest.java` |
| React shell | `frontend/src/App.jsx`, `workspace.css`, `components/layout/AppLayout.jsx`, `Sidebar.jsx`, `Topbar.jsx`, `components/WorkspaceControls.jsx` |
| Frontend context | `frontend/src/context/AuthContext.jsx`, `auth.js`, `workspace.js`, `routes/ProtectedRoute.jsx` |
| Frontend API and refresh | `frontend/src/api/axios.js`, `integrationApi.js`, `incidentApi.js`, `logApi.js`, `observabilityApi.js`, `frontend/src/hooks/useResource.js`, `frontend/src/observability.js` |
| Pages | `frontend/src/pages/Dashboard.jsx`, `LogExplorer.jsx`, `LogAnalysis.jsx`, `LogIngestion.jsx`, `Incidents.jsx`, `IncidentNotifications.jsx`, `Integrations.jsx`, `LogInspector.jsx`, `Login.jsx`, `Register.jsx` |
| Frontend test | `frontend/package.json`, `frontend/tests/observability.test.mjs` |
| Tooling | `scripts/kafka-runtime.ps1`, `test-stage08.ps1`, `stage08-notification-demo.py`, `stage09-dashboard-demo.py` |
| Documentation | `README.md`, `AGENTS.md`, `WORK_CONTINUATION.md`, `docs/SETUP_GUIDE.md`, and the Stage 09 reports and Stage 10 handoff in this directory |

No Git write or deployment was performed. Diagnostic artifacts and disposable database and broker storage are retained in protected local locations.
