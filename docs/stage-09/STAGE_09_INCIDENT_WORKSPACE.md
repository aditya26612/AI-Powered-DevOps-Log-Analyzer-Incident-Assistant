# Incident workspace

Incident listing shows persisted severity, lifecycle, event count, first and last occurrence, diagnosis projection, and updated time. It sorts by latest occurrence with a stable ID tie-breaker. Shareable filters and incident detail selection use query parameters.

The selected investigation shows actual lifecycle timestamps, rule and version, grouped affected applications, a paged chronology of source occurrences and membership-link times, reported trace provenance, and separate diagnosis and notification panels. A trace is evidence supplied by the collector. The UI renders only observed logs and their span identifiers.

Lifecycle updates use the backend version token and refresh after HTTP 409. The UI follows the existing OWNER, ADMIN and DEVOPS action permissions. VIEWER reads remain available.
