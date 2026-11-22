# Stage 09 security verification

The new observability endpoints verify the signed numeric user identity and organization membership before entering PostgreSQL tenant scope. The queries use explicit organization predicates and existing RLS. Application IDs are checked within the selected organization. Cross-tenant incident membership, source evidence, and outbox data stay under their existing boundaries.

Search values are bound parameters. A message search stays out of page URLs and request query strings. The log explorer renders React text and masks common credential patterns in the preview and original-event projection. The masked projection preserves line breaks. Redaction is heuristic and may miss secrets stored in unexpected formats. The existing log detail contract remains separately authorized for compatibility.

The browser keys its workspace by verified organization, aborts obsolete reads, and discards previous content when authorization is denied. Only authorized roles see write actions. Viewer accounts retain incident, diagnosis and notification history reads. A failed or revoked session uses the existing sign-in flow.
