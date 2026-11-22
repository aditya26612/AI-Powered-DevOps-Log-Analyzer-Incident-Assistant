# Live refresh

Overview polls every 20 seconds and the incident list and selected incident every 15 seconds. Selected diagnosis polls every five seconds while work can progress. Delivery and expanded attempt history poll every eight seconds. Log pages use deliberate refresh and retain a pinned occurrence range when paging.

Reads use an AbortController and a completion-scheduled timer. Hidden pages pause new scheduled reads. Visibility resumes polling. Scope changes and unmount cancel previous requests, and a snapshot is eligible to render only for its matching resource key. Retry and transition actions refresh the relevant view. Failures retain eligible data with a stale notice.

The browser test held an initial summary request open, verified the loading state, then released it to the real server. A later controlled HTTP 503 retained the authorized snapshot and displayed a stale notice. A subsequent successful read cleared the warning. Authorization failures discard stale content.
