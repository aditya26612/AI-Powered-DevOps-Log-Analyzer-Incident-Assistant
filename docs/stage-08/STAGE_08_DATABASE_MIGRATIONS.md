# Notification migrations

Flyway V8 adds notification and attempt tables, indexes, application policy/revision fields, and ownership constraints. V9 adds a shared destination gate for identical webhook registrations and two restricted lease routines. V1–V7 were preserved. The original PostgreSQL instance was read only during backup. The migrated fixture restored all three logical databases with matching historical rows, constraints, ACLs, roles, extensions, and sequences.

V8 and V9 validated against the populated V7 restore and a fresh schema. A second migration run made zero changes. A separate recovery database rebuilt V7 from the protected archive. Use a separate restore target for recovery. Notification history must be reconciled with any provider receipt before switching database state.

The 5432 development database received V8 and V9 after the fixture, regression, archive and quiescence gates passed. Flyway validated the result and reported a no-op rerun. Read-only checks confirmed versions 1–9, zero outbox records, and zero shared gate entries. Pre-cutover and post-cutover fingerprints matched for existing data, sequences, historical migration entries, and encrypted configuration. The complete backup retains 20 users, 4,388 logs including 785 unassigned, and 253 RAG rows.

All six application services returned HTTP 200 against this migrated database. The read-only preservation check passed again after the services stopped. The managed Kafka broker and PostgreSQL fixture stopped independently and kept their data directories.
