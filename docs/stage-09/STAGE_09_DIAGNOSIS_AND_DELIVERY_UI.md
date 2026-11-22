# Diagnosis and delivery

Diagnosis displays PENDING, RUNNING, RETRY_WAIT, SUCCEEDED, and FAILED_TERMINAL with attempt counts, retry time and failure category. Accepted results show the persisted summary, probable cause, suggested remediation and accepted time when available. Attempts have their own history. A manager can retry a terminal diagnosis subject to the backend budget.

The Slack section reads the outbox independently. It shows current state, safe destination label, timing and provider HTTP attempt history. An unknown outcome is an amber warning. Retrying it requires confirmation that a duplicate is possible. No webhook URL is returned by these views.
