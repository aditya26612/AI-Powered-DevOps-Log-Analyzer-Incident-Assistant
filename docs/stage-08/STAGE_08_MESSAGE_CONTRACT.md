# Slack message contract

The incoming webhook receives three plain-text Block Kit blocks and a fixed, safe fallback. Include severity, source or incident reference, configured app, occurrence times, and a stable delivery UUID. Single-app results use bounded redacted summary, cause, and suggested action. Multi-app results report that the full diagnosis is available in DevInsight.

Strip controls, avoid user-supplied URLs and mention syntax, disable unfurl, and truncate at Unicode code-point boundaries. Redaction is heuristic. Raw logs, exception dumps, full RAG prompts, rendered commands, and automatic clickable diagnosis links are omitted.
