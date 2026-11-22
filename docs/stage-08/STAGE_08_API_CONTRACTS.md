# Notification API contracts

Routes begin with /api/v1/organizations/{organization}. A signed human JWT with numeric identity and active membership is required.

| Method | Path after organization | Result |
| --- | --- | --- |
| GET | /incidents/{incident}/notifications | Incident notification page |
| GET | /notifications/{notification} | Safe destination label and provider status |
| GET | /notifications/{notification}/attempts | Paginated attempt history |
| POST | /notifications/{notification}/retry | 202 for a permitted recovery |
| PUT | /applications/{application}/slack/policy | 204 for owner/admin preference update |

Pages are zero-based with 10 entries and bounded offsets. Retry requires a manager and an eligible terminal state. For unknown outcome send confirmPossibleDuplicate=true. A conflicting retry returns 409. Foreign IDs return 404, and an ingestion key or unauthenticated request is rejected. No responses include leases, credentials, or raw provider replies.
