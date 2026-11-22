# Observability read contracts

All routes use a signed user token and verified organization membership under `/api/v1/organizations/{organization}`. App keys are limited to intake. An application filter uses its numeric ID encoded as a string by the frontend.

| Method and suffix | Response |
|---|---|
| GET `/observability/summary` | Time range, UTC buckets, severity and job counts, six unresolved incidents, eight activity entries |
| GET `/observability/logs` | Bounded log previews and tenant-scoped incident association |
| POST `/observability/logs` | Same read projection plus an optional JSON `text` literal message filter |
| GET `/observability/logs/{id}/source` | Redacted original persisted event and optional incident ID |
| GET `/incidents/{incident}/applications` | Paginated applications and member counts derived from persisted links |

The existing diagnosis detail response adds accepted probable cause, remediation, and completion time. Those texts come from the successful fenced result. Existing status, attempt, and retry fields are preserved.

Range is HOUR, DAY, WEEK or MONTH. Log filters cover application ID, source, severity, environment, V2 classification, ML status, source correlation/trace ID, incident and ERROR/FATAL together. Log pages are at most 50 records with a 200-page depth and stable occurrence/ID ordering. Pass the first response's `to` as `before` on later pages to pin the occurrence window. Late-arriving rows can still move offset positions. Literal message search requires 2–100 characters. All user values are bound parameters. There are no changes to ingestion routes or their response contracts.

The original `timestamp without time zone` columns retain historical source precision. Collectors normalize offset-bearing event times to UTC before persistence. Old wall-clock timestamps have no recoverable zone. Dashboard event occurrence uses the stored value as UTC and displays it in the browser zone.
