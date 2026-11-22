# Notification outbox model

The outbox row represents the intent and its single destination. It references the accepted diagnosis job and source log, optionally the incident, and the owner application. Composite foreign keys enforce organization, incident-owner, and application consistency. Unique keys constrain each diagnosis and each non-null incident to one notification per policy version.

The row binds the destination revision and tracks scheduling, expiry, lease, dispatch marker, policy suppression, and accepted acknowledgement time. Each claim has an attempt row with stable sequence number, start/end time, safe category, optional HTTP code and requester. Forced tenant RLS covers both tables. The runtime role has no DELETE grant.

The separate V9 destination gate tracks a keyed webhook digest, lease assignment, and availability time. It contains no tenant metadata or payload. Equal configured URLs can share the gate without joining or granting access to tenant delivery rows.
