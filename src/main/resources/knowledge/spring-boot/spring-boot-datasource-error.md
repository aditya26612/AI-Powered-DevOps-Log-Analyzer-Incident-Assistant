# Spring Boot DataSource and Database Connection Errors (Failed to configure a DataSource, HikariPool, PSQLException connection refused)

Technology: Spring Boot 3.x JDBC auto-configuration, HikariCP connection pool, PostgreSQL JDBC driver (pgjdbc). Scope: the application cannot create or obtain database connections. This is the client/application side; server-side PostgreSQL causes are in postgresql-connection-refused.md, postgresql-authentication-failure.md, and postgresql-too-many-connections.md.

## Problem: Spring Boot cannot connect to the database

Spring Boot auto-configures a DataSource from `spring.datasource.*` properties and prefers HikariCP when present. Failures fall into three groups: (1) the DataSource cannot be configured at all (no URL/driver), (2) the pool cannot open physical connections (network refused, timeout, authentication), (3) the pool is exhausted and callers time out waiting for a connection.

## Common Log Messages: Spring Boot datasource failure

Canonical (Spring Boot / pgjdbc source):

- `Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.`
- `Reason: Failed to determine a suitable driver class`
- pgjdbc: `Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.` (host:port varies)

Representative (HikariCP wording varies by version and pool name):

- `HikariPool-1 - Exception during pool initialization.`
- `HikariPool-1 - Connection is not available, request timed out after 30000ms.` (often `java.sql.SQLTransientConnectionException`)
- `org.postgresql.util.PSQLException: FATAL: password authentication failed for user "app"`
- `org.postgresql.util.PSQLException: The connection attempt failed.` with `java.net.UnknownHostException` or `SocketTimeoutException` as cause

## Symptoms: Spring Boot database connection problems

- Startup fails with `APPLICATION FAILED TO START` (configuration missing) or with a Hikari pool initialization exception.
- Application starts but requests fail intermittently with `Connection is not available, request timed out`.
- `/actuator/health` shows `db` component DOWN (if the health details are visible).

## Key Facts: Spring Boot datasource and HikariCP defaults

- Properties: `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`; `spring.datasource.driver-class-name` is optional when the driver can be inferred from the URL.
- Hikari-specific settings: `spring.datasource.hikari.*`, for example `spring.datasource.hikari.maximum-pool-size` and `spring.datasource.hikari.connection-timeout`.
- HikariCP defaults (HikariCP documentation): `maximumPoolSize` 10, `connectionTimeout` 30000 ms, `minimumIdle` equals `maximumPoolSize` when unset.
- Environment variables map by relaxed binding: `SPRING_DATASOURCE_URL` -> `spring.datasource.url`.

## Common Causes: Spring Boot datasource error

### Cause 1: No datasource URL or driver (Failed to configure a DataSource)

Why: properties missing for the active profile, env vars not passed into the container, or the JDBC driver dependency missing while a JDBC/JPA starter is present.
Supporting evidence: the canonical `'url' attribute is not specified` message; startup log shows unexpected active profiles.
Weakening evidence: the URL is printed/visible in configuration and the error is a connection exception instead.

### Cause 2: Database unreachable (Connection refused / UnknownHostException / timeout)

Why: wrong host or port, using `localhost` inside a container, database not yet started, server not listening on TCP for that interface.
Supporting evidence: pgjdbc `Connection to HOST:PORT refused`; `UnknownHostException`; connect timeouts.
Weakening evidence: a `FATAL:` message from PostgreSQL means the server was reached (then authentication or limits).
Next: postgresql-connection-refused.md and docker-container-network-error.md.

### Cause 3: Authentication / authorization failure

Supporting evidence: `PSQLException: FATAL: password authentication failed` or `no pg_hba.conf entry`.
Next: postgresql-authentication-failure.md.

### Cause 4: Pool exhaustion (Connection is not available, request timed out)

Why: all pool connections are in use for longer than `connectionTimeout`: slow queries, long transactions, connection leaks (connections not closed), or pool too small for concurrency.
Supporting evidence: timeout after ~30000 ms (default), active connections at `maximumPoolSize`, database itself healthy.
Weakening evidence: the database reports `too many clients` (server limit, see postgresql-too-many-connections.md).

### Cause 5: Server-side connection limit reached

Supporting evidence: `PSQLException: FATAL: sorry, too many clients already` during pool growth or with many replicas.
Next: postgresql-too-many-connections.md.

## Diagnostic Steps: Spring Boot datasource failure

1. Observation: read the innermost cause (Spring message, Hikari message, or PSQLException text).
2. If `Failed to configure a DataSource`: verify the active profile and that `spring.datasource.url` is provided (properties file, env var, config server).
3. If refused/unknown host: verify the JDBC host from the app's runtime environment (container/Pod), not from the developer's machine.
4. If `FATAL:` from PostgreSQL: the network path works; go to authentication or connection-limit documents.
5. If `Connection is not available`: compare pool size, request concurrency, and query durations; enable Hikari leak detection (`spring.datasource.hikari.leak-detection-threshold`) temporarily to find unclosed connections.
6. Conclusion: configuration vs network vs authentication vs pool exhaustion vs server limit.

## Useful Commands: Spring Boot datasource diagnostics

Read-only:

- Startup log line `The following 1 profile is active: "..."` (or `No active profile set, falling back to 1 default profile: "default"`): shows which profile-specific configuration applies.
- `GET /actuator/health`: includes the `db` health indicator; component details are visible only if `management.endpoint.health.show-details` allows it.
- `GET /actuator/env` (if exposed and secured): shows property sources; in Spring Boot 3 values are masked by default (`show-values` defaults to never).
- From the app container/Pod: `pg_isready -h HOST -p 5432` (if the client tools are installed) to test reachability with the same network view as the app.
- Metrics (if Micrometer/Actuator metrics are exposed): `hikaricp.connections.active`, `hikaricp.connections.pending`.

## Resolution: Spring Boot datasource errors

- Provide `spring.datasource.url/username/password` for the active profile, or via `SPRING_DATASOURCE_*` env vars; add the PostgreSQL driver dependency.
- Use the database's network name (Compose service name, Kubernetes Service DNS), not `localhost`, when the database runs elsewhere.
- Fix credentials or pg_hba rules on the server side for `FATAL` authentication errors.
- For pool exhaustion, fix leaks and slow transactions first; then size `maximum-pool-size` considering ALL replicas times pool size against PostgreSQL `max_connections`.
- Make startup resilient to a database that starts later (orchestrator readiness, retries) instead of increasing restart loops.

## Prevention: Spring Boot datasource failures

Externalize DB config per environment, keep pool sizes within the server budget, alert on pending connections, and add an integration test that starts against a real database.

## Diagnostic Differentiation: datasource error vs PostgreSQL server problems

- `Connection to HOST:5432 refused` (pgjdbc): TCP refused; server not listening there or wrong host. Server-side checks in postgresql-connection-refused.md.
- `FATAL: password authentication failed`: server reached, credentials rejected (postgresql-authentication-failure.md).
- `FATAL: sorry, too many clients already`: server limit (postgresql-too-many-connections.md).
- `Connection is not available, request timed out after ...ms`: client-side pool wait, not necessarily a database fault.
- `Failed to configure a DataSource`: pure configuration, the database was never contacted.

## Related Errors: Spring Boot DataSource and HikariCP connection errors

Terms related to Spring Boot DataSource and HikariCP connection errors: `Failed to configure a DataSource`, `Failed to determine a suitable driver class`, `HikariPool-1 - Exception during pool initialization`, `Connection is not available, request timed out`, `PSQLException`, `Connection refused`, `SQLTransientConnectionException`, `CannotCreateTransactionException`.

## Root-Cause Summary: Spring Boot DataSource and Database Connection Errors

Spring Boot DataSource errors. Typical rootCause statements: spring.datasource.url not provided for the active profile, database host unreachable or localhost used inside a container, credentials rejected by PostgreSQL, HikariCP pool exhausted (Connection is not available), server connection limit reached. Severity: high when startup fails or all requests need the database. Recommendation pattern: say whether the database was contacted at all, based on the presence of a FATAL server message. A pool timeout is a symptom inside the application; confirm on the database side before blaming PostgreSQL.
