# PostgreSQL Too Many Connections (sorry, too many clients already, remaining connection slots are reserved, max_connections, pooling)

Technology: PostgreSQL connection limits (max_connections, reserved slots), pg_stat_activity, connection pooling (application pools such as HikariCP, external poolers such as PgBouncer). Scope: the server rejects new sessions because connection slots are used up. Out of scope: server unreachable (postgresql-connection-refused.md), login rejected (postgresql-authentication-failure.md).

## Problem: PostgreSQL connection exhaustion

Each PostgreSQL connection is a server backend process counted against `max_connections` (typically default 100; requires restart to change). A number of slots are reserved for superusers (`superuser_reserved_connections`, default 3) and, since PostgreSQL 16, optionally for roles with `pg_use_reserved_connections` (`reserved_connections`, default 0). When ordinary slots are used up, new connections fail with SQLSTATE 53300 too_many_connections. The cause is almost always client behavior: too many pools x pool size, leaks, or idle-in-transaction sessions, not a database bug.

## Common Log Messages: PostgreSQL too many connections

Canonical:

- `FATAL:  sorry, too many clients already`
- Before PostgreSQL 16: `FATAL:  remaining connection slots are reserved for non-replication superuser connections`
- PostgreSQL 16 and later (wording reflects the reservation that applies):
  `FATAL:  remaining connection slots are reserved for roles with the SUPERUSER attribute`
  `FATAL:  remaining connection slots are reserved for roles with privileges of the "pg_use_reserved_connections" role`

Representative client-side:

- `org.postgresql.util.PSQLException: FATAL: sorry, too many clients already`
- HikariCP: `HikariPool-1 - Connection is not available, request timed out after 30000ms.` (client pool waiting; may be caused by, or unrelated to, the server limit)

## Symptoms: PostgreSQL connection slots exhausted

- New connections fail while existing ones keep working.
- Happens after scaling out application replicas, during traffic spikes, or gradually as leaked/idle sessions accumulate.
- Administrators may still connect as superuser via reserved slots.

## Common Causes: PostgreSQL too many clients

### Cause 1: Total pool capacity exceeds max_connections

Why: replicas x pool size per replica (+ other clients) > available slots. Example: 12 replicas x HikariCP default 10 = 120 connections, more than a default of 100.
Supporting evidence: errors after scaling; `pg_stat_activity` grouped by `application_name`/`client_addr` shows most connections from the app's pools, mostly `idle`.
Weakening evidence: few clients each with many connections in `active` state (load) or `idle in transaction` (leaks).

### Cause 2: Sessions stuck idle in transaction

Why: the application opened a transaction and did not commit/rollback (exception paths, long user think-time, missing transaction boundaries). These sessions hold connections and locks.
Supporting evidence: many rows with `state = 'idle in transaction'` and old `xact_start`.

### Cause 3: Connection leaks in the application

Why: connections borrowed and never returned; pools grow to max, or apps without pools open new connections per request.
Supporting evidence: connection count grows steadily with traffic and never falls; HikariCP leak detection warnings.

### Cause 4: Slow queries hold connections longer

Supporting evidence: many `active` sessions with long-running queries (`now() - query_start`), lock waits (`wait_event_type = 'Lock'`).

### Cause 5: max_connections set too low for the topology

Supporting evidence: steady connection usage close to the limit under normal load with healthy pool sizes.
Note: raising `max_connections` increases memory and process overhead; pooling usually beats raising the limit.

## Diagnostic Steps: PostgreSQL connection exhaustion

1. Observation: confirm the exact FATAL message and SQLSTATE 53300.
2. Connect via a reserved slot (superuser) and measure usage vs limit: `SHOW max_connections;` and `SELECT count(*) FROM pg_stat_activity;`.
3. Group sessions by state, application and client to find the dominant consumer (query below).
4. Hypothesis: mostly `idle` from pools -> pool sizing; `idle in transaction` -> transaction leaks; `active` long queries -> performance; steady growth -> leaks.
5. Verify with application pool configuration (`maximum-pool-size` x replicas) and pool metrics.
6. Conclusion: name the consumer and the mechanism.

## Useful Commands: PostgreSQL connection diagnostics

Read-only:

- `SHOW max_connections;` / `SHOW superuser_reserved_connections;` (and `SHOW reserved_connections;` on PostgreSQL 16+).
- Sessions by state and client:

```sql
SELECT state, application_name, client_addr, count(*) AS connections
FROM pg_stat_activity
GROUP BY state, application_name, client_addr
ORDER BY connections DESC;
```

- Idle-in-transaction sessions:

```sql
SELECT pid, usename, datname, application_name, client_addr, xact_start, state, query
FROM pg_stat_activity
WHERE state LIKE 'idle in transaction%'
ORDER BY xact_start;
```

`client_addr` is NULL for Unix-socket connections. Note that `pg_stat_activity` also lists background processes; filter with `backend_type = 'client backend'` when counting client sessions.

State-changing (WARNING):

- `SELECT pg_terminate_backend(PID);` terminates one backend (sends SIGTERM). Its open transaction is rolled back and the client sees an error. Permission: same role, members of `pg_signal_backend`, or superuser (only superusers can terminate superuser backends). Use on identified leaked/idle-in-transaction sessions, not as a bulk "kill all" routine.
- `ALTER SYSTEM SET idle_in_transaction_session_timeout = '5min';` then `SELECT pg_reload_conf();`: automatically terminates sessions idle in a transaction longer than the timeout (default 0 = disabled). Pick a value that does not break legitimate workflows. `idle_session_timeout` (PostgreSQL 14+) similarly closes idle sessions; avoid it with poolers that keep idle connections intentionally.
- Changing `max_connections` requires a server restart.

## Resolution: PostgreSQL too many connections

- Budget connections: sum of (replicas x max pool size) for all apps + admin/maintenance headroom must stay below `max_connections` minus reserved slots. Reduce HikariCP `maximum-pool-size` per replica if needed.
- Introduce a connection pooler (for example PgBouncer in transaction pooling mode) when many app instances need connections; transaction pooling breaks some session-level features (session state, some prepared-statement usage), so validate the application.
- Fix transaction handling to eliminate `idle in transaction` sessions; set `idle_in_transaction_session_timeout` as a safety net.
- Fix leaks (close connections, use framework-managed transactions).
- Raise `max_connections` only with memory sizing and a planned restart.

## Prevention: PostgreSQL connection exhaustion

Track connections per application (`application_name`), alert at ~80% of `max_connections`, include connection budgets in scaling decisions, and use poolers for high replica counts.

## Diagnostic Differentiation: too many connections vs pool timeout vs refused

- `sorry, too many clients already`: server-side limit (this document).
- `Connection is not available, request timed out` without server FATAL errors: client pool exhausted inside the app (spring-boot-datasource-error.md).
- `Connection refused`: no listener (postgresql-connection-refused.md).
- `remaining connection slots are reserved ...`: same exhaustion, but the reserved slots are still free for privileged roles.

## Related Errors: PostgreSQL too many connections

Terms related to PostgreSQL too many connections: `sorry, too many clients already`, `remaining connection slots are reserved`, `53300`, `too_many_connections`, `max_connections`, `superuser_reserved_connections`, `reserved_connections`, `idle in transaction`, `pg_stat_activity`, `PgBouncer`, `connection pool`.

## Root-Cause Summary: PostgreSQL Too Many Connections

PostgreSQL too many connections. Typical rootCause statements: replicas times pool size exceed max_connections, sessions stuck idle in transaction, connection leaks, slow queries holding connections, max_connections too low for the topology. Severity: high because new sessions fail for all clients except reserved roles. Recommendation pattern: cite pg_stat_activity counts by state and application, and prefer pooling and pool budgets over raising max_connections.
