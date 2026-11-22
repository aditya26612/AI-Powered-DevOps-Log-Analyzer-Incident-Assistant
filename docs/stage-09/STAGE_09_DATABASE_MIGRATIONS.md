# Database migration status

Stage 09 uses the existing V9 schema. There are no new SQL migrations or changes to Flyway history. Summary and log detail derive read-only projections from existing tenant-scoped tables. The development instance was used only for read-only health, preserved-row verification and protected backup creation.

A separate PostgreSQL 16 copy restored all three databases. Row and sequence fingerprints, constraint semantics, extension metadata, and effective ACLs matched. The V9 destination gate's explicit owner ACL is rendered as a default owner ACL after restore and has the same effective grants.

All six normal services reached HTTP 200. Historical fingerprints and the persistent environment fingerprint still matched after managed shutdown. The disposable PostgreSQL and Kafka services were stopped separately and their storage was retained. The normal PostgreSQL and Ollama instances remained available.
